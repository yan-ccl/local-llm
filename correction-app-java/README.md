# correction-app-java

Java/Spring Boot port of `correction-app`.

It keeps the same layered design:

- deterministic rules: punctuation, spaces, repeated punctuation, blacklist
- whitelist protection for terms that must not be modified
- optional Ollama LLM candidate source for missing/redundant/grammar fixes
- confidence filtering and deterministic merge arbitration

The Java port does not directly load `macbert4csc`. The MacBERT source is left out on purpose because the Python version depends on PyTorch/Transformers/ONNX runtime. In this Java project, `macbert` reports unavailable by default; rules and Ollama LLM still work.

## Requirements

- JDK 17+
- Maven 3.9+
- Optional: Ollama running at `http://localhost:11434`

## Run

```bash
mvn spring-boot:run
```

Or build a jar:

```bash
mvn package
java -jar target/correction-app-java-0.1.0.jar
```

The service listens on `http://localhost:8000`.

Open the web page:

```bash
http://localhost:8000/
```

## Run With Docker

```bash
docker build -t correction-app-java .
docker run --rm -p 8000:8000 correction-app-java
```

If Ollama is running on the host, pass its URL explicitly:

```bash
docker run --rm -p 8000:8000 \
  -e OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  correction-app-java
```

Or let Compose start both Ollama and the Java service:

```bash
docker compose up --build
```

## API

```bash
curl -X POST http://localhost:8000/correct \
  -H "Content-Type: application/json" \
  -d '{"text":"我的帐号的新情设置丢失了。","mode":"standard"}'
```

Modes:

- `quick`: rules only in the current Java port
- `standard`: rules + LLM when available and needed
- `deep`: always asks LLM when available

Health:

```bash
curl http://localhost:8000/health
curl http://localhost:8000/modes
```

## Environment

| Variable | Default | Description |
|---|---:|---|
| `SERVER_PORT` | `8000` | HTTP port |
| `CSC_ENABLE_LLM` | `true` | Enable Ollama LLM source |
| `LLM_MODEL` | `qwen2.5:1.5b` | Ollama model name |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama base URL |
| `DATA_DIR` | classpath `data/` | Optional external directory containing `blacklist.txt`, `confusion.txt`, `whitelist.txt` |

`OLLAMA_URL` and `OLLAMA_HOST` are also accepted. If they include `/api/generate` or `/api/chat`, the suffix is stripped.
