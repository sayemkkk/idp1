/* Public anonymous review submission page. */

const LIKERT = [
  { value: 1, label: 'Strongly disagree' },
  { value: 2, label: 'Disagree' },
  { value: 3, label: 'Neutral' },
  { value: 4, label: 'Agree' },
  { value: 5, label: 'Strongly agree' },
];

let token = null;
let questions = [];

document.addEventListener('DOMContentLoaded', init);

async function init() {
  const params = new URLSearchParams(window.location.search);
  token = params.get('token');

  if (!token) {
    showError('No review link token was provided. Please use the exact link shared with you.');
    return;
  }

  try {
    const data = await API.get('/api/public/forms/' + encodeURIComponent(token));
    questions = data.questions || [];
    if (questions.length === 0) {
      showError('This review form has no questions.');
      return;
    }
    renderReview(data.form, questions);
  } catch (err) {
    showError(err.message || 'This review link is invalid or has been closed.');
  }
}

function showError(message) {
  document.getElementById('loading').classList.add('hidden');
  document.getElementById('review').classList.add('hidden');
  document.getElementById('success').classList.add('hidden');
  document.getElementById('error-text').textContent = message;
  document.getElementById('error-box').classList.remove('hidden');
}

function renderReview(form, qs) {
  document.getElementById('loading').classList.add('hidden');
  document.getElementById('review').classList.remove('hidden');

  document.getElementById('review-title').textContent =
    (form.title && form.title.trim()) || ('Review: ' + form.teacherName);
  document.getElementById('review-sub').textContent =
    `${form.teacherName} · ${form.courseName}`;

  const container = document.getElementById('questions');
  container.innerHTML = qs.map((q, idx) => {
    const options = LIKERT.map((opt) => `
      <label>
        <input type="radio" name="q_${q.id}" value="${opt.value}" required />
        <span class="num">${opt.value}</span>
        <span>${esc(opt.label)}</span>
      </label>`).join('');
    return `
      <div class="question">
        <div class="q-label"><span class="q-num">${idx + 1}</span><span>${esc(q.text)}</span></div>
        <div class="likert">${options}</div>
      </div>`;
  }).join('');

  document.getElementById('review-form').addEventListener('submit', onSubmit);
}

async function onSubmit(e) {
  e.preventDefault();
  const errorBox = document.getElementById('submit-error');
  const submitBtn = document.getElementById('submit-btn');
  errorBox.classList.add('hidden');

  const answers = {};
  for (const q of questions) {
    const chosen = document.querySelector(`input[name="q_${q.id}"]:checked`);
    if (!chosen) {
      errorBox.textContent = 'Please answer every question before submitting.';
      errorBox.classList.remove('hidden');
      e.target.querySelector(`input[name="q_${q.id}"]`).scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    answers[q.id] = parseInt(chosen.value, 10);
  }

  const comment = document.getElementById('comment').value.trim() || null;

  submitBtn.disabled = true;
  submitBtn.textContent = 'Submitting…';
  try {
    await API.post('/api/public/forms/' + encodeURIComponent(token) + '/submit', { answers, comment });
    document.getElementById('review').classList.add('hidden');
    document.getElementById('success').classList.remove('hidden');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } catch (err) {
    errorBox.textContent = err.message;
    errorBox.classList.remove('hidden');
    submitBtn.disabled = false;
    submitBtn.textContent = 'Submit anonymous feedback';
  }
}
