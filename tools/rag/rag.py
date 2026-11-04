#!/usr/bin/env python3
"""
RAG local do Kabayero Miner - busca e perguntas sobre a documentacao e o codigo,
100% offline e sem dependencias alem do Python 3.8+.

  python tools/rag/rag.py index                 # (re)constroi o indice em tools/rag/index.json
  python tools/rag/rag.py ask "como criar a carteira?"
  python tools/rag/rag.py chat                  # modo interativo
  python tools/rag/rag.py ask -k 8 --no-llm "..."   # so os trechos, sem modelo de linguagem

Como funciona:
  1. Le README.md, docs/*.md, CHANGELOG.md e o codigo Kotlin em app/src, divide em trechos
     (por titulos do markdown ou blocos de ~60 linhas de codigo) e monta um indice BM25
     (ranqueamento classico por termos, sem rede e sem GPU).
  2. Na pergunta, recupera os trechos mais relevantes e os mostra com arquivo e linha.
  3. Se o Ollama estiver rodando (http://localhost:11434), envia pergunta + trechos ao modelo
     local (padrao: llama3.2, mude com --model ou RAG_MODEL) e imprime a resposta. Sem Ollama,
     os trechos ja respondem a maioria das duvidas.
"""
import argparse
import json
import math
import os
import re
import sys
import unicodedata
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
INDEX_PATH = os.path.join(os.path.dirname(__file__), "index.json")
SOURCES = ["README.md", "CHANGELOG.md", "docs", os.path.join("app", "src", "main"), os.path.join("app", "src", "test"), os.path.join("tools", "rag", "README.md")]
EXTS = {".md", ".kt", ".xml", ".kts", ".pro", ".yml", ".txt"}
STOP = set("""a o os as um uma uns umas de do da dos das em no na nos nas por para com sem que se e ou ao aos
mas como mais muito ja nao sim ser ter esta este isto isso essa esse aquele aquela ele ela eles elas seu sua seus suas
the of to in and or is are be it this that for on with as at by from an val var fun return null true false
import package private public override class object""".split())


def strip_accents(s: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFKD", s) if not unicodedata.combining(c))


def tokenize(text: str):
    text = strip_accents(text.lower())
    toks = re.findall(r"[a-z0-9]+", text)
    return [t for t in toks if len(t) > 1 and t not in STOP]


def iter_files():
    for src in SOURCES:
        p = os.path.join(ROOT, src)
        if os.path.isfile(p):
            yield p
        elif os.path.isdir(p):
            for dirpath, dirnames, filenames in os.walk(p):
                dirnames[:] = [d for d in dirnames if d not in ("build", "res") or d == "res"]
                for f in filenames:
                    if os.path.splitext(f)[1] in EXTS and "bip39_english" not in f:
                        yield os.path.join(dirpath, f)


def chunk_markdown(lines):
    """Um trecho por secao (titulo ate o proximo titulo), com limite de ~70 linhas."""
    chunks, start, buf = [], 0, []
    for i, line in enumerate(lines):
        if line.startswith("#") and buf and (len(buf) > 3):
            chunks.append((start, buf))
            start, buf = i, []
        buf.append(line)
        if len(buf) >= 70:
            chunks.append((start, buf))
            start, buf = i + 1, []
    if buf:
        chunks.append((start, buf))
    return chunks


def chunk_code(lines, size=60, overlap=10):
    chunks, i = [], 0
    while i < len(lines):
        chunks.append((i, lines[i:i + size]))
        i += size - overlap
    return chunks


def build_index():
    docs = []
    for path in iter_files():
        try:
            with open(path, encoding="utf-8", errors="replace") as fh:
                lines = fh.read().splitlines()
        except OSError:
            continue
        rel = os.path.relpath(path, ROOT).replace("\\", "/")
        chunks = chunk_markdown(lines) if path.endswith(".md") else chunk_code(lines)
        for start, buf in chunks:
            text = "\n".join(buf).strip()
            if len(text) < 40:
                continue
            docs.append({"file": rel, "line": start + 1, "text": text, "tokens": tokenize(text)})
    df = {}
    for d in docs:
        for t in set(d["tokens"]):
            df[t] = df.get(t, 0) + 1
    avgdl = sum(len(d["tokens"]) for d in docs) / max(1, len(docs))
    index = {"docs": docs, "df": df, "avgdl": avgdl, "n": len(docs)}
    with open(INDEX_PATH, "w", encoding="utf-8") as fh:
        json.dump(index, fh, ensure_ascii=False)
    print(f"indice criado: {len(docs)} trechos de {len(set(d['file'] for d in docs))} arquivos -> {os.path.relpath(INDEX_PATH, ROOT)}")
    return index


def load_index():
    if not os.path.exists(INDEX_PATH):
        return build_index()
    with open(INDEX_PATH, encoding="utf-8") as fh:
        return json.load(fh)


def bm25(index, query, k=5, k1=1.5, b=0.75):
    q = tokenize(query)
    if not q:
        return []
    n, avgdl, df = index["n"], index["avgdl"], index["df"]
    scored = []
    for d in index["docs"]:
        toks = d["tokens"]
        if not toks:
            continue
        tf = {}
        for t in toks:
            tf[t] = tf.get(t, 0) + 1
        score = 0.0
        for t in q:
            if t not in tf:
                continue
            idf = math.log(1 + (n - df.get(t, 0) + 0.5) / (df.get(t, 0) + 0.5))
            freq = tf[t]
            score += idf * (freq * (k1 + 1)) / (freq + k1 * (1 - b + b * len(toks) / avgdl))
        if d["file"].endswith(".md"):
            score *= 1.3   # documentacao em primeiro lugar
        if score > 0:
            scored.append((score, d))
    scored.sort(key=lambda x: -x[0])
    return scored[:k]


def ollama_available(host):
    try:
        with urllib.request.urlopen(host + "/api/tags", timeout=2) as r:
            return r.status == 200
    except Exception:
        return False


def ask_llm(host, model, question, hits):
    context = "\n\n".join(f"[{i + 1}] {d['file']}:{d['line']}\n{d['text'][:1800]}" for i, (_, d) in enumerate(hits))
    prompt = (
        "Voce e o assistente do Kabayero Miner, um minerador de Bitcoin para Android com carteira de 24 palavras. "
        "Responda em portugues, de forma direta e correta, usando APENAS os trechos abaixo. Se a resposta nao estiver "
        "nos trechos, diga que nao encontrou na documentacao. Cite os trechos usados como [n].\n\n"
        f"TRECHOS:\n{context}\n\nPERGUNTA: {question}\nRESPOSTA:"
    )
    body = json.dumps({"model": model, "prompt": prompt, "stream": True}).encode()
    req = urllib.request.Request(host + "/api/generate", data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=600) as r:
        for line in r:
            try:
                piece = json.loads(line.decode("utf-8"))
            except ValueError:
                continue
            sys.stdout.write(piece.get("response", ""))
            sys.stdout.flush()
            if piece.get("done"):
                break
    print()


def show_hits(hits, width=700):
    for i, (score, d) in enumerate(hits, 1):
        print(f"\n[{i}] {d['file']}:{d['line']}  (relevancia {score:.1f})")
        print("-" * 72)
        text = d["text"]
        print(text if len(text) <= width else text[:width] + " ...")


def main():
    # Windows: garante acentos corretos no terminal
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except (AttributeError, ValueError):
            pass
    ap = argparse.ArgumentParser(description="RAG local do Kabayero Miner")
    ap.add_argument("cmd", choices=["index", "ask", "chat"])
    ap.add_argument("question", nargs="*")
    ap.add_argument("-k", "--top", type=int, default=5, help="quantos trechos recuperar")
    ap.add_argument("--model", default=os.environ.get("RAG_MODEL", "llama3.2"), help="modelo do Ollama")
    ap.add_argument("--host", default=os.environ.get("OLLAMA_HOST", "http://localhost:11434"))
    ap.add_argument("--no-llm", action="store_true", help="nao usar o Ollama, so mostrar os trechos")
    args = ap.parse_intermixed_args()   # permite opcoes antes ou depois da pergunta

    if args.cmd == "index":
        build_index()
        return
    index = load_index()
    use_llm = (not args.no_llm) and ollama_available(args.host)
    if not args.no_llm and not use_llm:
        print("(Ollama nao encontrado em %s: mostrando apenas os trechos relevantes. Instale em https://ollama.com e rode "
              "'ollama pull %s' para respostas geradas.)" % (args.host, args.model))

    def answer(q):
        hits = bm25(index, q, k=args.top)
        if not hits:
            print("nada encontrado; tente outras palavras.")
            return
        show_hits(hits)
        if use_llm:
            print("\n" + "=" * 72 + f"\nRESPOSTA ({args.model}):\n")
            ask_llm(args.host, args.model, q, hits)

    if args.cmd == "ask":
        q = " ".join(args.question).strip()
        if not q:
            ap.error("informe a pergunta")
        answer(q)
    else:
        print("Kabayero Miner - RAG local. Digite a pergunta (vazio para sair).")
        while True:
            try:
                q = input("\npergunta> ").strip()
            except (EOFError, KeyboardInterrupt):
                break
            if not q:
                break
            answer(q)


if __name__ == "__main__":
    main()
