# Changelog

## 1.0.1 (2026-11-05)

- Correção: os campos de texto (palavras da carteira, verificação, envio, config) ficavam escondidos atrás do teclado; agora o conteúdo sobe com o teclado e o campo em foco permanece visível.
- CI: workflow usa o Android SDK pré-instalado do runner e cache do Gradle.

## 1.0.0 (2026-11-04)

Primeira versão pública.

- Carteira BIP39 de 24 palavras com endereço SegWit nativo (BIP84) e zpub; palavras exibidas uma única vez com captura bloqueada e confirmação de 4 palavras; nada secreto é persistido.
- Mineração SHA-256d com cliente Stratum V1 (TCP/TLS, IPv4 primeiro), modo solo (ckpool, public-pool) ou pool (OCEAN) ou servidor personalizado; serviço em primeiro plano com wake lock.
- Barra de potência de 0 a 100% da capacidade do aparelho, convertida em threads + intensidade e aplicada ao vivo.
- Proteção térmica configurável na aba Segurança: temperatura de pausa e de retorno, temperatura atual, carregador obrigatório e bateria mínima.
- Receber BTC (QR, copiar, compartilhar) e Enviar BTC (assinatura em memória com as 24 palavras digitadas na hora; ECDSA/RFC 6979, BIP143, RBF, locktime; taxas do mempool.space).
- Bloqueio biométrico, relatório de integridade do aparelho, dados públicos cifrados com AES-256-GCM no Android Keystore, backups desativados.
- Interface dark estilo Matrix com chuva digital, logo vetorial próprio e painel de expectativa honesta.
- Link para o Instagram @cavaleiro.ai na tela inicial e na aba Config.
- 55 testes unitários com vetores oficiais; validado em Galaxy Tab A8 (Android 14) e Moto G 2014 (Android 10, ARM 32 bits).
- Documentação completa em `docs/` e RAG local em `tools/rag/`.
