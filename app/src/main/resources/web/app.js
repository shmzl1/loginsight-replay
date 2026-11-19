const byId = id => document.getElementById(id);
const form = byId('upload');
const file = byId('file');
const format = byId('format');
const status = byId('status');
const buckets = byId('buckets');
const requests = byId('requests');
const timeline = byId('timeline');
let selection = 0;

async function query(route, data = {}) {
  const response = await fetch('/api/' + route, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  const result = await response.json();
  if (!response.ok) {
    throw new Error(result.error);
  }
  return result;
}

function showStatus(message, error = false) {
  status.textContent = message;
  status.dataset.error = error;
}

function clearTimeline() {
  timeline.replaceChildren();
  byId('source').textContent = '选择时间线事件查看来源';
  byId('raw').textContent = '';
}

function showBuckets(items) {
  selection++;
  clearTimeline();
  requests.replaceChildren(new Option('请先选择错误时段', ''));
  requests.disabled = true;
  buckets.replaceChildren();
  if (!items.length) {
    buckets.textContent = '暂无 ERROR 事件';
    return;
  }
  const max = items.reduce((highest, item) => Math.max(highest, item.errorCount), 1);
  for (const item of items) {
    const button = document.createElement('button');
    button.type = 'button';
    button.setAttribute('aria-pressed', 'false');
    const bar = document.createElement('meter');
    bar.max = max;
    bar.value = item.errorCount;
    button.append(item.minuteStart.replace('T', ' ').slice(0, 16), bar, item.errorCount + ' ERROR');
    button.addEventListener('click', async () => {
      const current = ++selection;
      clearTimeline();
      requests.replaceChildren(new Option('请选择失败请求', ''));
      requests.disabled = true;
      for (const sibling of buckets.children) {
        sibling.setAttribute('aria-pressed', String(sibling === button));
      }
      try {
        const { failedRequestIds } = await query('requests', { minuteStart: item.minuteStart });
        if (current !== selection) return;
        for (const id of failedRequestIds) requests.add(new Option(id, id));
        requests.disabled = failedRequestIds.length === 0;
      } catch (error) {
        if (current === selection) showStatus(error.message, true);
      }
    });
    buckets.append(button);
  }
}

requests.addEventListener('change', async () => {
  const current = ++selection;
  clearTimeline();
  if (!requests.value) return;
  try {
    const result = await query('timeline', { requestId: requests.value });
    if (current !== selection) return;
    for (const event of result.timeline) {
      const li = document.createElement('li');
      const button = document.createElement('button');
      const metadata = document.createElement('span');
      button.type = 'button';
      button.setAttribute('aria-pressed', 'false');
      metadata.textContent = [event.timestamp, event.service, event.level].join(' · ');
      button.append(metadata, event.message);
      button.addEventListener('click', () => {
        for (const entry of timeline.querySelectorAll('button')) {
          entry.setAttribute('aria-pressed', String(entry === button));
        }
        byId('source').textContent = event.sourceFile + ' · 第 ' + event.lineNumber + ' 行 · 来源 ' + event.sourceId;
        byId('raw').textContent = event.rawLine;
      });
      li.append(button);
      timeline.append(li);
    }
  } catch (error) {
    if (current === selection) showStatus(error.message, true);
  }
});

file.addEventListener('change', () => {
  format.value = file.files[0]?.name.toLowerCase().endsWith('.jsonl') ? 'jsonl' : 'text';
});

form.addEventListener('submit', async event => {
  event.preventDefault();
  const chosen = file.files[0];
  if (!chosen) return;
  const selectedFormat = format.value;
  const submit = form.querySelector('button');
  submit.disabled = true;
  showStatus('正在导入 ' + chosen.name);
  try {
    if (chosen.size > 8 * 1024 * 1024) throw new Error('日志文件超过 8 MiB');
    const content = new TextDecoder('utf-8', { fatal: true }).decode(await chosen.arrayBuffer());
    const result = await query('import', { file: chosen.name, format: selectedFormat, content });
    showBuckets(result.errorBuckets);
    showStatus(chosen.name + ' 已导入 · ' + result.imported + ' 条事件');
  } catch (error) {
    showStatus(error.message, true);
  } finally {
    submit.disabled = false;
  }
});

const initialSelection = selection;
query('buckets').then(result => {
  if (initialSelection === selection && result.errorBuckets.length) showBuckets(result.errorBuckets);
}).catch(error => showStatus(error.message, true));
