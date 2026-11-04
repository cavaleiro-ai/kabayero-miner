# RAG local (perguntas sobre o app, offline)

Uma ferramenta de linha de comando que indexa toda a documentação e o código do Kabayero Miner no seu próprio computador e responde perguntas sobre eles. Não precisa de internet, conta ou chave de API: usa busca BM25 em Python puro e, se você tiver o [Ollama](https://ollama.com) instalado, um modelo de linguagem local escreve a resposta final.

## Requisitos

- Python 3.8 ou mais novo (`python --version`).
- Opcional: Ollama com um modelo baixado, por exemplo `ollama pull llama3.2`.

## Uso

Na pasta do projeto:

```bash
# 1) construir o índice (repita quando a documentação ou o código mudar)
python tools/rag/rag.py index

# 2) perguntar
python tools/rag/rag.py ask "como criar a carteira e onde ficam as 24 palavras?"
python tools/rag/rag.py ask "o que a barra de potencia faz?"
python tools/rag/rag.py ask "como o envio de btc assina a transacao?"

# 3) modo conversa
python tools/rag/rag.py chat
```

Opções úteis:

| Opção | Efeito |
| --- | --- |
| `-k 8` | recupera 8 trechos em vez de 5 |
| `--no-llm` | mostra só os trechos (sem Ollama) |
| `--model mistral` | escolhe outro modelo do Ollama (ou variável `RAG_MODEL`) |
| `--host http://192.168.0.10:11434` | Ollama em outra máquina (ou variável `OLLAMA_HOST`) |

## O que é indexado

`README.md`, `CHANGELOG.md`, tudo em `docs/`, o código Kotlin e recursos em `app/src/main`, e os testes em `app/src/test`. Arquivos de markdown são divididos por título; código em blocos de 60 linhas com sobreposição. O índice fica em `tools/rag/index.json` (ignorado pelo git; gere localmente).

## Como funciona por dentro

1. Cada trecho vira uma lista de termos (minúsculas, sem acentos, sem palavras vazias).
2. A pergunta é pontuada contra cada trecho com BM25 (frequência do termo, raridade no conjunto, tamanho do trecho). Trechos de documentação recebem um pequeno bônus sobre código.
3. Os melhores trechos são impressos com arquivo e linha, para você abrir a fonte.
4. Com Ollama disponível, pergunta e trechos vão para o modelo com a instrução de responder apenas com base neles e citar `[n]`.

Tudo roda localmente: nenhum texto sai do seu computador.
