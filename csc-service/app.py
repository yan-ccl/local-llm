"""macbert4csc Chinese spelling correction service."""
import os
import time
import logging
from contextlib import asynccontextmanager
from typing import List, Tuple

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from transformers import BertTokenizerFast, AutoModelForMaskedLM

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger("csc-service")

MODEL_NAME = os.environ.get("CSC_MODEL", "shibing624/macbert4csc-base-chinese")
MAX_LEN = int(os.environ.get("CSC_MAX_LEN", "510"))

state = {}


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Loading model %s ...", MODEL_NAME)
    tokenizer = BertTokenizerFast.from_pretrained(MODEL_NAME)
    model = AutoModelForMaskedLM.from_pretrained(MODEL_NAME)
    model.eval()
    state["tokenizer"] = tokenizer
    state["model"] = model
    logger.info("Model loaded.")
    yield
    state.clear()


app = FastAPI(title="macbert4csc CSC Service", lifespan=lifespan)


class CorrectRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=2000)


class ErrorItem(BaseModel):
    original: str
    corrected: str
    position: int


class CorrectResponse(BaseModel):
    original: str
    corrected: str
    errors: List[ErrorItem]
    elapsed_ms: float


def _diff_errors(origin: str, corrected: str) -> Tuple[str, List[ErrorItem]]:
    """Align corrected output with the original text and collect substitutions.

    macbert4csc returns char-level predictions of the same length as the input,
    but tokenizer.decode may insert spaces or drop special chars. We preserve
    whitespace/punctuation from the source to keep positions stable.
    """
    keep_chars = {" ", "\n", "\t", "　", "“", "”", "‘", "’", "…", "—"}
    errors: List[ErrorItem] = []
    aligned = list(corrected)
    for i, ch in enumerate(origin):
        if ch in keep_chars:
            if i < len(aligned):
                aligned.insert(i, ch)
            else:
                aligned.append(ch)
            continue
        if i >= len(aligned):
            break
        if ch != aligned[i]:
            # Preserve case differences for ASCII letters.
            if ch.lower() == aligned[i].lower():
                aligned[i] = ch
                continue
            errors.append(ErrorItem(original=ch, corrected=aligned[i], position=i))
    final = "".join(aligned)[: len(origin)]
    return final, errors


@torch.no_grad()
def correct_text(text: str) -> CorrectResponse:
    tokenizer = state["tokenizer"]
    model = state["model"]
    if len(text) > MAX_LEN:
        raise HTTPException(status_code=400, detail=f"text too long (>{MAX_LEN} chars)")

    start = time.perf_counter()
    inputs = tokenizer([text], padding=True, return_tensors="pt", truncation=True, max_length=MAX_LEN + 2)
    outputs = model(**inputs)
    decoded = tokenizer.decode(torch.argmax(outputs.logits[0], dim=-1), skip_special_tokens=True).replace(" ", "")
    corrected_raw = decoded[: len(text)]
    corrected, errors = _diff_errors(text, corrected_raw)
    elapsed_ms = (time.perf_counter() - start) * 1000

    return CorrectResponse(
        original=text,
        corrected=corrected,
        errors=errors,
        elapsed_ms=round(elapsed_ms, 2),
    )


@app.get("/health")
def health():
    return {"status": "ok", "model_loaded": "model" in state}


@app.post("/correct", response_model=CorrectResponse)
def correct(req: CorrectRequest):
    return correct_text(req.text)
