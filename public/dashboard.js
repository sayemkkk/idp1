/* HOD dashboard logic: form CRUD, link generation, and analytics. */

let currentUser = null;
let currentAnalyticsFormId = null;

const DEFAULT_QUESTIONS = [
  'The teacher explains concepts clearly.',
  'The teacher is well prepared for each class.',
  'The course content is well organised and relevant.',
  'The teacher encourages questions and discussion.',
  'Assessment and grading are fair and transparent.',
];

document.addEventListener('DOMContentLoaded', init);

async function init() {
  try {
    currentUser = await requireSession();
  } catch { return; }

  if (currentUser.role !== 'HOD') {
    window.location.href = '/admin.html';
    return;
  }

  document.getElementById('hod-name').textContent = currentUser.fullName || currentUser.username;
  document.getElementById('who').innerHTML =
    `<strong>${esc(currentUser.fullName || currentUser.username)}</strong> · ${esc(currentUser.department || 'No department')}`;

  document.querySelectorAll('.tab').forEach((tab) => {
    tab.addEventListener('click', () => switchTab(tab.dataset.tab));
  });

  document.getElementById('add-question').addEventListener('click', () => addQuestionField(''));
  document.getElementById('create-form').addEventListener('submit', onCreateSubmit);

  DEFAULT_QUESTIONS.forEach((q) => addQuestionField(q));

  loadForms();
}

function switchTab(name) {
  document.querySelectorAll('.tab').forEach((t) => {
    t.classList.toggle('active', t.dataset.tab === name);
  });
  document.getElementById('tab-forms').classList.toggle('hidden', name !== 'forms');
  document.getElementById('tab-create').classList.toggle('hidden', name !== 'create');
  document.getElementById('tab-analytics').classList.toggle('hidden', name !== 'analytics');
}

/* ------------------------------------------------------------------ */
/* Create form                                                         */
/* ------------------------------------------------------------------ */

function addQuestionField(text) {
  const list = document.getElementById('question-list');
  const row = document.createElement('div');
  row.className = 'flex';
  row.style.marginBottom = '8px';
  row.innerHTML = `
    <input type="text" class="question-input" placeholder="e.g. The teacher is punctual." value="${esc(text)}" />
    <button type="button" class="btn btn-ghost btn-sm" title="Remove">✕</button>
  `;
  row.querySelector('button').addEventListener('click', () => {
    if (list.children.length > 1) row.remove();
  });
  list.appendChild(row);
}

async function onCreateSubmit(e) {
  e.preventDefault();
  const errorBox = document.getElementById('create-error');
  const createBtn = document.getElementById('create-btn');
  errorBox.classList.add('hidden');

  const questions = Array.from(document.querySelectorAll('.question-input'))
    .map((i) => i.value.trim())
    .filter((v) => v.length > 0);

  if (questions.length === 0) {
    errorBox.textContent = 'Please add at least one question.';
    errorBox.classList.remove('hidden');
    return;
  }

  const payload = {
    teacherName: document.getElementById('teacherName').value.trim(),
    courseName: document.getElementById('courseName').value.trim(),
    title: document.getElementById('title').value.trim() || null,
    questions,
  };

  createBtn.disabled = true;
  createBtn.textContent = 'Creating…';
  try {
    const data = await API.post('/api/forms', payload);
    const fullLink = window.location.origin + data.reviewLink;
    document.getElementById('created-link').textContent = fullLink;
    document.getElementById('created-result').dataset.link = fullLink;
    document.getElementById('created-result').classList.remove('hidden');
    e.target.reset();
    document.getElementById('question-list').innerHTML = '';
    DEFAULT_QUESTIONS.forEach((q) => addQuestionField(q));
    document.getElementById('created-result').scrollIntoView({ behavior: 'smooth' });
    loadForms();
  } catch (err) {
    errorBox.textContent = err.message;
    errorBox.classList.remove('hidden');
  } finally {
    createBtn.disabled = false;
    createBtn.textContent = 'Create form & generate link';
  }
}

async function copyCreatedLink() {
  const link = document.getElementById('created-result').dataset.link;
  const ok = await copyText(link);
  if (ok) alert('Link copied to clipboard!');
}

/* ------------------------------------------------------------------ */
/* Forms list                                                          */
/* ------------------------------------------------------------------ */

async function loadForms() {
  const loading = document.getElementById('forms-loading');
  const empty = document.getElementById('forms-empty');
  const container = document.getElementById('forms-container');
  loading.classList.remove('hidden');
  empty.classList.add('hidden');
  container.innerHTML = '';

  try {
    const data = await API.get('/api/forms');
    loading.classList.add('hidden');
    if (!data.forms || data.forms.length === 0) {
      empty.classList.remove('hidden');
      return;
    }
    data.forms.forEach((f) => container.appendChild(renderFormCard(f)));
  } catch (err) {
    loading.classList.add('hidden');
    container.innerHTML = `<div class="alert alert-error">${esc(err.message)}</div>`;
  }
}

function renderFormCard(f) {
  const card = document.createElement('div');
  card.className = 'card form-card';
  const fullLink = window.location.origin + '/review.html?token=' + f.token;
  const statusPill = f.open
    ? '<span class="pill pill-open">Open</span>'
    : '<span class="pill pill-closed">Closed</span>';

  card.innerHTML = `
    <div class="section-head" style="margin-bottom:8px;">
      <div>
        <h2 style="margin-bottom:2px;">${esc(f.teacherName)}</h2>
        <div class="muted">${esc(f.courseName)}${f.title ? ' · ' + esc(f.title) : ''}</div>
      </div>
      ${statusPill}
    </div>
    <div class="muted" style="margin-bottom:12px;">
      ${f.responseCount} response${f.responseCount === 1 ? '' : 's'} · Created ${formatDate(f.createdAt)}
    </div>
    <div class="link-box" style="margin-bottom:12px;">
      <code>${esc(fullLink)}</code>
      <button class="btn btn-ghost btn-sm" data-copy="${esc(fullLink)}">Copy</button>
    </div>
    <div class="flex">
      <button class="btn btn-sm" data-action="analytics">View analytics</button>
      <button class="btn btn-ghost btn-sm" data-action="toggle">${f.open ? 'Close' : 'Reopen'}</button>
      <button class="btn btn-danger btn-sm right" data-action="delete">Delete</button>
    </div>
  `;

  card.querySelector('[data-copy]').addEventListener('click', async (e) => {
    const ok = await copyText(e.target.dataset.copy);
    e.target.textContent = ok ? 'Copied!' : 'Failed';
    setTimeout(() => { e.target.textContent = 'Copy'; }, 1500);
  });

  card.querySelector('[data-action="analytics"]').addEventListener('click', () => openAnalytics(f));
  card.querySelector('[data-action="toggle"]').addEventListener('click', async () => {
    try {
      await API.put('/api/forms/' + f.id, { open: !f.open });
      loadForms();
    } catch (err) { alert(err.message); }
  });
  card.querySelector('[data-action="delete"]').addEventListener('click', async () => {
    if (!confirm(`Delete the review form for "${f.teacherName}"? This also deletes all its responses and cannot be undone.`)) return;
    try {
      await API.del('/api/forms/' + f.id);
      loadForms();
    } catch (err) { alert(err.message); }
  });

  return card;
}

/* ------------------------------------------------------------------ */
/* Analytics                                                           */
/* ------------------------------------------------------------------ */

async function openAnalytics(form) {
  currentAnalyticsFormId = form.id;
  switchTab('analytics');
  document.getElementById('analytics-title').textContent = form.teacherName;
  document.getElementById('analytics-sub').textContent = form.courseName + (form.title ? ' · ' + form.title : '');
  document.getElementById('analytics-loading').classList.remove('hidden');
  document.getElementById('analytics-body').classList.add('hidden');

  try {
    const data = await API.get('/api/forms/' + form.id + '/analytics');
    document.getElementById('analytics-loading').classList.add('hidden');
    document.getElementById('analytics-body').classList.remove('hidden');

    document.getElementById('stat-avg').textContent = data.overallAverage.toFixed(2) + ' / 5';
    document.getElementById('stat-count').textContent = data.responseCount;
    document.getElementById('stat-questions').textContent = data.questionStats.length;

    const statsList = document.getElementById('stats-list');
    if (data.questionStats.length === 0) {
      statsList.innerHTML = '<p class="muted">No questions.</p>';
    } else {
      statsList.innerHTML = data.questionStats.map((s) => {
        const pct = (s.average / 5) * 100;
        return `
          <div class="rating-row">
            <div class="q-text">${esc(s.text)}</div>
            <div class="bar"><span style="width:${pct.toFixed(1)}%"></span></div>
            <div class="rating-value">${s.average.toFixed(2)}</div>
          </div>`;
      }).join('');
    }

    const commentsList = document.getElementById('comments-list');
    if (!data.comments || data.comments.length === 0) {
      commentsList.innerHTML = '<p class="muted">No written comments yet.</p>';
    } else {
      commentsList.innerHTML = data.comments.map((c) => `
        <div class="comment">
          <div class="meta">${formatDate(c.submittedAt)} · Anonymous</div>
          <div>${esc(c.comment)}</div>
        </div>`).join('');
    }
  } catch (err) {
    document.getElementById('analytics-loading').classList.add('hidden');
    document.getElementById('analytics-body').classList.remove('hidden');
    document.getElementById('stats-list').innerHTML = `<div class="alert alert-error">${esc(err.message)}</div>`;
  }
}
