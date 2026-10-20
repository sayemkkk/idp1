/* Super-admin console: manage HOD accounts. */

document.addEventListener('DOMContentLoaded', init);

async function init() {
  let user;
  try {
    user = await requireSession();
  } catch { return; }

  if (user.role !== 'SUPER_ADMIN') {
    window.location.href = '/dashboard.html';
    return;
  }

  document.getElementById('who').innerHTML =
    `<strong>${esc(user.fullName || user.username)}</strong>`;

  document.getElementById('new-account-btn').addEventListener('click', () => {
    const box = document.getElementById('create-box');
    box.classList.toggle('hidden');
  });
  document.getElementById('cancel-create').addEventListener('click', () => {
    document.getElementById('create-box').classList.add('hidden');
  });
  document.getElementById('account-form').addEventListener('submit', onCreateAccount);

  loadOverview();
  loadAccounts();
}

async function loadOverview() {
  try {
    const data = await API.get('/api/admin/overview');
    document.getElementById('stat-hods').textContent = data.hods;
    document.getElementById('stat-admins').textContent = data.superAdmins;
  } catch (err) {
    console.error(err);
  }
}

async function loadAccounts() {
  const loading = document.getElementById('accounts-loading');
  const empty = document.getElementById('accounts-empty');
  const wrap = document.getElementById('accounts-table-wrap');
  const body = document.getElementById('accounts-body');
  loading.classList.remove('hidden');
  empty.classList.add('hidden');
  wrap.classList.add('hidden');

  try {
    const data = await API.get('/api/admin/users');
    loading.classList.add('hidden');
    if (!data.users || data.users.length === 0) {
      empty.classList.remove('hidden');
      return;
    }
    wrap.classList.remove('hidden');
    body.innerHTML = '';
    data.users.forEach((u) => body.appendChild(renderRow(u)));
  } catch (err) {
    loading.classList.add('hidden');
    body.innerHTML = `<tr><td colspan="6"><div class="alert alert-error">${esc(err.message)}</div></td></tr>`;
    wrap.classList.remove('hidden');
  }
}

function renderRow(u) {
  const tr = document.createElement('tr');
  const statusPill = u.active
    ? '<span class="pill pill-open">Active</span>'
    : '<span class="pill pill-closed">Disabled</span>';

  tr.innerHTML = `
    <td>${esc(u.fullName || '—')}</td>
    <td><code>${esc(u.username)}</code></td>
    <td>${esc(u.department || '—')}</td>
    <td>${statusPill}</td>
    <td class="muted">${formatDate(u.createdAt)}</td>
    <td class="actions">
      <button class="btn btn-ghost btn-sm" data-action="toggle">${u.active ? 'Disable' : 'Enable'}</button>
      <button class="btn btn-ghost btn-sm" data-action="reset">Reset PW</button>
      <button class="btn btn-danger btn-sm" data-action="delete">Delete</button>
    </td>
  `;

  tr.querySelector('[data-action="toggle"]').addEventListener('click', async () => {
    try {
      await API.put('/api/admin/users/' + u.id, { active: !u.active });
      loadAccounts();
      loadOverview();
    } catch (err) { alert(err.message); }
  });

  tr.querySelector('[data-action="reset"]').addEventListener('click', async () => {
    const pw = prompt('Enter a new password for ' + u.username + ' (min 6 characters):');
    if (!pw) return;
    try {
      await API.put('/api/admin/users/' + u.id, { password: pw });
      alert('Password updated.');
    } catch (err) { alert(err.message); }
  });

  tr.querySelector('[data-action="delete"]').addEventListener('click', async () => {
    if (!confirm(`Delete account "${u.username}"? All their forms and responses will also be deleted.`)) return;
    try {
      await API.del('/api/admin/users/' + u.id);
      loadAccounts();
      loadOverview();
    } catch (err) { alert(err.message); }
  });

  return tr;
}

async function onCreateAccount(e) {
  e.preventDefault();
  const errorBox = document.getElementById('create-error');
  const submit = document.getElementById('account-submit');
  errorBox.classList.add('hidden');

  const payload = {
    fullName: document.getElementById('fullName').value.trim(),
    department: document.getElementById('department').value.trim(),
    username: document.getElementById('username').value.trim(),
    password: document.getElementById('password').value,
    role: document.getElementById('role').value,
  };

  submit.disabled = true;
  submit.textContent = 'Creating…';
  try {
    await API.post('/api/admin/users', payload);
    e.target.reset();
    document.getElementById('create-box').classList.add('hidden');
    loadAccounts();
    loadOverview();
  } catch (err) {
    errorBox.textContent = err.message;
    errorBox.classList.remove('hidden');
  } finally {
    submit.disabled = false;
    submit.textContent = 'Create account';
  }
}
