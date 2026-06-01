const state = {
  mode: "quick",
  busy: false
};

const sourceText = document.querySelector("#sourceText");
const charCount = document.querySelector("#charCount");
const submitButton = document.querySelector("#submitButton");
const clearButton = document.querySelector("#clearButton");
const healthButton = document.querySelector("#healthButton");
const correctedText = document.querySelector("#correctedText");
const elapsed = document.querySelector("#elapsed");
const sources = document.querySelector("#sources");
const runState = document.querySelector("#runState");
const editsBody = document.querySelector("#editsBody");
const editCount = document.querySelector("#editCount");
const notes = document.querySelector("#notes");
const serviceStatus = document.querySelector("#serviceStatus");
const modeButtons = Array.from(document.querySelectorAll("[data-mode]"));

function setBusy(value) {
  state.busy = value;
  submitButton.disabled = value;
  submitButton.textContent = value ? "处理中" : "纠错";
}

function updateCharCount() {
  charCount.textContent = `${sourceText.value.length} / 2000`;
}

function setMode(mode) {
  state.mode = mode;
  modeButtons.forEach((button) => {
    const active = button.dataset.mode === mode;
    button.classList.toggle("is-active", active);
    button.setAttribute("aria-checked", active ? "true" : "false");
  });
}

function setRunState(text, kind = "") {
  runState.textContent = text;
  runState.className = `run-state ${kind}`.trim();
}

function renderChips(items) {
  sources.replaceChildren();
  if (!items || items.length === 0) {
    return;
  }
  items.forEach((item) => {
    const chip = document.createElement("span");
    chip.className = "chip";
    chip.textContent = item;
    sources.appendChild(chip);
  });
}

function renderEdits(edits) {
  editsBody.replaceChildren();
  const rows = Array.isArray(edits) ? edits : [];
  editCount.textContent = `${rows.length} 条`;

  if (rows.length === 0) {
    const tr = document.createElement("tr");
    const td = document.createElement("td");
    td.className = "empty-cell";
    td.colSpan = 6;
    td.textContent = "暂无修改";
    tr.appendChild(td);
    editsBody.appendChild(tr);
    return;
  }

  rows.forEach((edit) => {
    const tr = document.createElement("tr");
    [
      `${edit.start}-${edit.end}`,
      edit.original || "插入",
      edit.suggestion || "删除",
      edit.type || "-",
      edit.source || "-",
      typeof edit.confidence === "number" ? edit.confidence.toFixed(2) : "-"
    ].forEach((value) => {
      const td = document.createElement("td");
      td.textContent = value;
      tr.appendChild(td);
    });
    editsBody.appendChild(tr);
  });
}

function renderNotes(items) {
  if (!items || items.length === 0) {
    notes.hidden = true;
    notes.textContent = "";
    return;
  }
  notes.hidden = false;
  notes.textContent = items.join("；");
}

function renderResult(data) {
  correctedText.classList.toggle("empty", !data.corrected);
  correctedText.textContent = data.corrected || "无结果";
  elapsed.textContent = typeof data.elapsed_ms === "number" ? `${data.elapsed_ms} ms` : "已完成";
  renderChips(data.sources_used);
  renderEdits(data.edits);
  renderNotes(data.notes);
  setRunState(data.degraded ? "已降级" : "已完成", data.degraded ? "warn" : "ok");
}

async function refreshHealth() {
  try {
    const response = await fetch("/health", { headers: { Accept: "application/json" } });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const data = await response.json();
    const caps = data.capabilities || {};
    serviceStatus.textContent = `服务正常 · rule=${Boolean(caps.rule)} · llm=${Boolean(caps.llm)} · macbert=${Boolean(caps.macbert)}`;
    serviceStatus.className = "status-line ok";
  } catch (error) {
    serviceStatus.textContent = `服务不可用 · ${error.message}`;
    serviceStatus.className = "status-line warn";
  }
}

async function loadModes() {
  try {
    const response = await fetch("/modes", { headers: { Accept: "application/json" } });
    if (!response.ok) {
      return;
    }
    const data = await response.json();
    if (data.default) {
      setMode(data.default);
    }
  } catch (_error) {
    // Keep local defaults.
  }
}

async function correct() {
  const text = sourceText.value.trim();
  if (!text) {
    correctedText.classList.add("empty");
    correctedText.textContent = "请输入文本";
    setRunState("待处理");
    renderEdits([]);
    renderChips([]);
    renderNotes([]);
    elapsed.textContent = "未运行";
    return;
  }

  setBusy(true);
  setRunState("处理中");
  correctedText.classList.add("empty");
  correctedText.textContent = "处理中...";

  try {
    const response = await fetch("/correct", {
      method: "POST",
      headers: {
        "Content-Type": "application/json; charset=utf-8",
        Accept: "application/json"
      },
      body: JSON.stringify({ text, mode: state.mode })
    });

    if (!response.ok) {
      const message = await response.text();
      throw new Error(message || `HTTP ${response.status}`);
    }

    const data = await response.json();
    renderResult(data);
  } catch (error) {
    correctedText.classList.add("empty");
    correctedText.textContent = "请求失败";
    setRunState(error.message, "error");
  } finally {
    setBusy(false);
  }
}

sourceText.addEventListener("input", updateCharCount);
submitButton.addEventListener("click", correct);
clearButton.addEventListener("click", () => {
  sourceText.value = "";
  updateCharCount();
  correctedText.classList.add("empty");
  correctedText.textContent = "等待输入";
  elapsed.textContent = "未运行";
  renderChips([]);
  renderEdits([]);
  renderNotes([]);
  setRunState("待处理");
  sourceText.focus();
});
healthButton.addEventListener("click", refreshHealth);
modeButtons.forEach((button) => button.addEventListener("click", () => setMode(button.dataset.mode)));

updateCharCount();
loadModes();
refreshHealth();
