"""Side-by-side comparison UI: macbert4csc vs Qwen2.5 LLM."""
import os
import time
import logging
from concurrent.futures import ThreadPoolExecutor
from typing import Tuple

import gradio as gr
import requests

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("web-ui")

CSC_URL = os.environ.get("CSC_URL", "http://csc-service:8000/correct")
OLLAMA_URL = os.environ.get("OLLAMA_URL", "http://ollama:11434/api/generate")
LLM_MODEL = os.environ.get("LLM_MODEL", "qwen2.5:3b")
LLM_TIMEOUT = int(os.environ.get("LLM_TIMEOUT", "180"))
CSC_TIMEOUT = int(os.environ.get("CSC_TIMEOUT", "60"))

LLM_PROMPT = """你是一位严谨的中文校对专家。请仔细检查下面这段文本中可能存在的错误，包括：错字、错词、错句、语病、标点错误、用词不当等。

要求：
1. 如果存在错误，按"【错误清单】"逐条列出（说明错在哪里、应改成什么、为什么）。
2. 给出"【修改后文本】"完整版本。
3. 如完全无误，回答：未发现错误。

原文：
{text}
"""


def call_csc(text: str) -> Tuple[str, str]:
    start = time.perf_counter()
    try:
        r = requests.post(CSC_URL, json={"text": text}, timeout=CSC_TIMEOUT)
        r.raise_for_status()
        data = r.json()
    except requests.RequestException as e:
        return f"**调用失败**：{e}", "—"

    elapsed = (time.perf_counter() - start) * 1000
    errors = data.get("errors", [])

    md = f"**修改后文本：**\n\n> {data['corrected']}\n\n"
    if errors:
        md += f"**发现 {len(errors)} 处错误：**\n\n"
        for err in errors:
            md += f"- 位置 {err['position']}：「{err['original']}」→「{err['corrected']}」\n"
    else:
        md += "_未发现错误_\n"
    return md, f"{elapsed:.0f} ms"


def call_llm(text: str) -> Tuple[str, str]:
    start = time.perf_counter()
    try:
        r = requests.post(
            OLLAMA_URL,
            json={
                "model": LLM_MODEL,
                "prompt": LLM_PROMPT.format(text=text),
                "stream": False,
                "options": {"temperature": 0.2, "num_predict": 600},
            },
            timeout=LLM_TIMEOUT,
        )
        r.raise_for_status()
        data = r.json()
    except requests.RequestException as e:
        return f"**调用失败**：{e}", "—"

    elapsed = time.perf_counter() - start
    response_text = data.get("response", "").strip()
    return response_text or "_（模型未返回内容）_", f"{elapsed:.1f} s"


def run_comparison(text: str):
    if not text or not text.strip():
        return "_请输入文本_", "—", "_请输入文本_", "—"
    with ThreadPoolExecutor(max_workers=2) as pool:
        f_csc = pool.submit(call_csc, text)
        f_llm = pool.submit(call_llm, text)
        csc_md, csc_t = f_csc.result()
        llm_md, llm_t = f_llm.result()
    return csc_md, csc_t, llm_md, llm_t


EXAMPLES = [
    "今天新情很好，我和同事去公园里散布，看见了很多漂亮的话。",
    "这份报告的内容非常详尽，但有几个地方需要进一步的核实，请你在明天之前完成修该并发送给我。",
    "由于天气原因，原定于明天举行的户外活动将推迟至下周三，请大家相互转告，按时参加。",
]


with gr.Blocks(title="中文文本纠错对比 Demo", theme=gr.themes.Soft()) as demo:
    gr.Markdown(
        """
        # 中文文本纠错对比 Demo

        左：**macbert4csc**（专用 CSC 小模型，CPU 毫秒级，专攻错字错词）
        右：**Qwen2.5-3B-Instruct**（通用小 LLM，CPU 秒级，可识别错句/语病/标点）

        建议输入 100-200 字的中文文本进行测试。
        """
    )

    with gr.Row():
        input_text = gr.Textbox(
            label="待校对文本",
            placeholder="请粘贴或输入要校对的中文文本……",
            lines=8,
        )

    with gr.Row():
        submit_btn = gr.Button("开始纠错对比", variant="primary", scale=3)
        clear_btn = gr.Button("清空", scale=1)

    with gr.Row():
        with gr.Column():
            gr.Markdown("### macbert4csc")
            csc_time = gr.Textbox(label="耗时", interactive=False)
            csc_output = gr.Markdown(label="结果")
        with gr.Column():
            gr.Markdown(f"### Qwen2.5 ({LLM_MODEL})")
            llm_time = gr.Textbox(label="耗时", interactive=False)
            llm_output = gr.Markdown(label="结果")

    gr.Examples(examples=EXAMPLES, inputs=input_text, label="示例文本")

    submit_btn.click(
        run_comparison,
        inputs=input_text,
        outputs=[csc_output, csc_time, llm_output, llm_time],
    )
    clear_btn.click(
        lambda: ("", "", "—", "", "—"),
        outputs=[input_text, csc_output, csc_time, llm_output, llm_time],
    )


if __name__ == "__main__":
    demo.queue().launch(server_name="0.0.0.0", server_port=7860, show_api=False)
