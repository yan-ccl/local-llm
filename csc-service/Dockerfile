FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    HF_HOME=/models \
    TRANSFORMERS_CACHE=/models \
    OMP_NUM_THREADS=4

WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install -r requirements.txt

# Pre-download the model into the image so containers start without network.
RUN python -c "from transformers import BertTokenizerFast, AutoModelForMaskedLM; \
    BertTokenizerFast.from_pretrained('shibing624/macbert4csc-base-chinese'); \
    AutoModelForMaskedLM.from_pretrained('shibing624/macbert4csc-base-chinese')"

COPY app.py .

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8000/health || exit 1

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
