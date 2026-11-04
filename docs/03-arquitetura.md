# Arquitetura e documentação técnica

Documento para quem vai ler, auditar ou modificar o código. Linguagem: Kotlin 2.2, Jetpack Compose (Material 3), minSdk 23 (Android 6.0), targetSdk 35. Sem bibliotecas de criptografia de terceiros: toda a parte Bitcoin é implementada no projeto e verificada por vetores oficiais nos testes.

## 1. Mapa do código

```
app/src/main/kotlin/br/com/cavaleiros/mineradores/
├── App.kt                    Application: repositório e wordlist BIP39
├── MainActivity.kt           Activity única: barras do sistema escuras, bloqueio biométrico, permissões, serviço
├── wallet/
│   ├── Hash.kt               SHA-256, SHA-256d, RIPEMD-160 (próprio), HMAC-SHA256/512, PBKDF2-HMAC-SHA512, hex
│   ├── Secp256k1.kt          Curva em coordenadas afins com BigInteger (ponto, soma, dobro, multiplicação, SEC1)
│   ├── Ecdsa.kt              Assinatura ECDSA: nonce RFC 6979, S baixo, DER, verificação
│   ├── Encoding.kt           Base58Check, Bech32/Bech32m (BIP173/BIP350)
│   ├── Bip39.kt              Entropia -> palavras -> semente (PBKDF2 2048 iterações, NFKD)
│   ├── Bip32.kt              Chaves estendidas, derivação normal e endurecida, serialização xpub/zpub
│   ├── WalletFactory.kt      BIP84: m/84'/0'/0'/0/0 -> bc1q, zpub; SpendingKeyFactory; AddressValidator
│   └── Transaction.kt        Scripts de saída, serialização, sighash BIP143, planejador de envio, assinador
├── mining/
│   ├── Sha256Miner.kt        SHA-256d com midstate otimizado para cabeçalhos de 80 bytes
│   ├── StratumJob.kt         Coinbase, raiz de Merkle, cabeçalho, alvos (dificuldade <-> target)
│   ├── StratumClient.kt      Cliente Stratum V1 (TCP/TLS, IPv4 primeiro, keepalive, reconexão)
│   └── MiningEngine.kt       Workers, potência ao vivo, estatísticas, submissão de shares, log
├── security/
│   ├── KeystoreCrypto.kt     AES-256-GCM com chave no Android Keystore (StrongBox -> TEE -> software)
│   ├── SecureStore.kt        Preferências cifradas
│   └── DeviceIntegrity.kt    Root, depurador, emulador, ADB, bloqueio de tela, nível do Keystore
├── data/
│   ├── Models.kt             MinerSettings (potência, temperaturas...), CpuLoad, pools, WalletInfo
│   ├── AppRepository.kt      Estado persistido (StateFlow)
│   ├── BalanceApi.kt         Saldo via mempool.space (opcional)
│   └── ChainApi.kt           UTXOs, taxas, altura, broadcast via mempool.space (envio)
├── service/MiningService.kt  Serviço em primeiro plano, wake lock, monitor de bateria/temperatura, notificação
└── ui/
    ├── theme/                Cores e tipografia Matrix
    ├── components/           MatrixRain, PowerBar, ShieldLogo, QrCode, FLAG_SECURE, widgets
    ├── MainViewModel.kt      Carteira pendente, verificação, envio, saldo, integridade
    ├── AppNavigation.kt      Rotas e abas (Minerar, Carteira, Config, Segurança)
    └── screens/              Onboarding, criação/confirmação, importação, verificação, painel, config, carteira, receber/enviar, segurança
app/src/test/                 Vetores oficiais e testes do núcleo (55 testes)
tools/rag/                    RAG local (BM25 + Ollama opcional)
docs/                         Esta documentação
```

## 2. Fluxo de dados

```
[Tela]  --ações-->  [MainViewModel]  --transform-->  [AppRepository] --cifra--> [SecureStore/Keystore]
                            |                               |
                            |                          StateFlow (wallet, settings)
                            v                               v
                    [MiningService] <--- observa settings (potência, temperaturas, carregador)
                            |
                    [MiningEngine] ---- stats (StateFlow) ----> [Painel]
                            |
                    [StratumClient] <==== TCP/TLS ====> pool
```

- A UI nunca fala com o pool diretamente. Tudo passa pelo serviço, que sobrevive à tela desligada.
- `Miner.engine` é um singleton; o serviço o inicia/para, o ViewModel só lê `stats`.
- Mudanças em `settings` (barra de potência, temperaturas) são aplicadas pelo serviço sem reiniciar a conexão.

## 3. Carteira

### 3.1 BIP39
- Entropia de 256 bits de `SecureRandom` -> 24 palavras (8 bits de checksum do SHA-256 da entropia).
- Semente: PBKDF2-HMAC-SHA512 com 2048 iterações, senha = palavras normalizadas (NFKD), sal = `"mnemonic" + passphrase`.
- A wordlist em `res/raw/bip39_english.txt` tem SHA-256 `2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda`, verificado em teste.

### 3.2 BIP32 / BIP84
- Chave mestra: HMAC-SHA512(chave "Bitcoin seed", semente).
- Caminho da conta `m/84'/0'/0'`; primeiro endereço `m/84'/0'/0'/0/0`; endereço = Bech32 (`bc`, versão 0, HASH160 da chave pública comprimida).
- `zpub` = chave pública estendida da conta com versão `0x04B24746`.
- Todo material secreto (`ByteArray`) é zerado com `fill(0)` após o uso; `ExtendedKey.wipe()`.

### 3.3 O que é persistido
Somente `WalletInfo(address, zpub, masterFingerprint, derivationPath, label, createdAt, source)` e `MinerSettings`, serializados em JSON e cifrados com AES-256-GCM. A chave AES vive no Keystore com alias `cavaleiros_mineradores_master_v1`, tentando StrongBox e caindo para TEE/software. O blob é `versão | tamanho do IV | IV | texto cifrado`.

## 4. Mineração

### 4.1 Stratum V1
Mensagens JSON por linha. Sequência: `mining.subscribe` -> `mining.suggest_difficulty` -> `mining.authorize(endereco.worker, senha)` -> `mining.suggest_difficulty` de novo (alguns pools ignoram a primeira). Notificações tratadas: `mining.notify`, `mining.set_difficulty`, `mining.set_extranonce`, `client.reconnect`, `client.show_message`, `mining.ping`. Resolução DNS com todos os endereços, IPv4 primeiro (operadoras móveis com IPv6 quebrado). TLS com `SSLSocketFactory` padrão e verificação de hostname.

### 4.2 Montagem do bloco
`coinbase = coinb1 || extranonce1 || extranonce2 || coinb2`; raiz de Merkle a partir do `merkle_branch`; `prevhash` convertido de palavras de 32 bits; cabeçalho de 80 bytes com campos little-endian (`version, prevhash, merkleroot, ntime, nbits, nonce`). `Target.fromDifficulty` usa `BigDecimal` com 100 dígitos para não perder precisão em dificuldades fracionárias.

### 4.3 SHA-256d com midstate
Os primeiros 64 bytes do cabeçalho são constantes por job: o estado intermediário é calculado uma vez e cada tentativa só processa o bloco final (16 bytes + padding) e a segunda compressão. Filtro rápido: só compara com o alvo quando os 16 bits finais do hash são zero. Vetores: bloco gênesis e bloco 125552.

### 4.4 Workers e potência
O motor cria `cores` workers; `activeThreads` (0..cores) e `intensityNow` (0..100) são `@Volatile` e ajustados por `setLoad()` sem reconectar. Cada worker tem espaço próprio de `extranonce2` (`id + k*count`). A intensidade é um ciclo de trabalho: após cada lote de 4096 hashes o worker dorme `busy * (100 - i) / i`. `CpuLoad.fromPower(p, cores)`: `load = p/100*cores`, `threads = ceil(load)`, `intensity = load/threads*100`.

### 4.5 Serviço e proteções
`MiningService` (`foregroundServiceType="specialUse"`) mantém wake lock parcial e, a cada 5 s, lê bateria (intent sticky) e `settings`: pausa por carregador ausente, bateria baixa ou `temp >= maxBatteryTempC`; pausa térmica só retoma com `temp <= resumeBatteryTempC`. A notificação mostra hashrate, shares e estado, com ação "Parar".

## 5. Envio de BTC

### 5.1 Planejamento (`SendPlanner`)
- Entradas: UTXOs confirmados do endereço, ordenados por valor decrescente, acumulados até cobrir `valor + taxa`.
- Tamanho virtual estimado: 11 vB de overhead + 68 vB por entrada P2WPKH + tamanho de cada saída (31 vB P2WPKH, 43 vB P2TR, 34 vB P2PKH, 32 vB P2SH).
- Troco volta ao próprio endereço; se ficar abaixo de 546 sats é absorvido pela taxa.
- "Enviar tudo" usa todas as entradas e uma saída.
- `nSequence = 0xFFFFFFFD` (sinaliza RBF) e `nLockTime = altura atual` (anti fee-sniping), versão 2.

### 5.2 Assinatura (`TxSigner`, `Ecdsa`)
- Sighash BIP143 (`SIGHASH_ALL`) por entrada, com `scriptCode = 1976a914{hash160}88ac` e o valor da entrada.
- ECDSA secp256k1 com nonce determinístico RFC 6979 (HMAC-SHA256), S baixo (BIP62) e DER mínimo (BIP66). Cada assinatura é verificada localmente antes da transmissão.
- Witness por entrada: `[assinatura || 0x01, chave pública comprimida]`; `scriptSig` vazio.
- Vetor de teste: exemplo "Native P2WPKH" do BIP143 reproduz hashPrevouts, hashSequence, hashOutputs, sighash e a assinatura byte a byte.

### 5.3 Ciclo de vida do segredo no envio
```
texto das palavras (UI) -> Bip39.splitWords -> SpendingKeyFactory.fromMnemonic (semente -> m/84'/0'/0'/0/0)
  -> confere address == salvo -> TxSigner.sign -> key.wipe(); campos da UI limpos em ON_STOP e onDispose
```
Nenhum passo grava em disco. A tela usa `FLAG_SECURE`.

### 5.4 Rede (`ChainApi`)
`GET /api/address/{a}/utxo`, `GET /api/v1/fees/recommended`, `GET /api/blocks/tip/height`, `POST /api/tx` em `https://mempool.space`. HTTPS obrigatório (texto puro bloqueado por `network_security_config`).

## 6. Segurança da aplicação
- `allowBackup=false` e `dataExtractionRules` vazias: nada vai para backup em nuvem ou transferência entre aparelhos.
- `FLAG_SECURE` com contagem de referências (`SecureWindowCounter`) nas telas de criação, palavras, confirmação, verificação e envio.
- Bloqueio por `BiometricPrompt` (biometria fraca ou credencial do aparelho) com carência de 30 s em segundo plano.
- Relatório de integridade: caminhos e pacotes de root, `Debug.isDebuggerConnected`, `FLAG_DEBUGGABLE`, heurísticas de emulador, `adb_enabled`, bloqueio de tela, nível do Keystore.
- Permissões: `INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE(_SPECIAL_USE)`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `USE_BIOMETRIC`.
- R8 com minificação no release; `queries` apenas para apps de root e Instagram.

## 7. Testes
`./gradlew testDebugUnitTest` roda 55 testes: BIP39 (vetores Trezor), BIP32 (vetor 1), BIP84, RIPEMD-160, Base58/Bech32/BIP350, SHA-256d (gênesis, bloco 125552), alvos e dificuldade, montagem de job sintético, `CpuLoad`, ECDSA/RFC 6979 (4 vetores), BIP143, scripts de endereço, planejador e assinador. Com `-Dstratum.live=true` rodam também testes contra pools reais (job atual vs. ponta da cadeia; motor completo com shares aceitos).

## 8. Build e distribuição
- `./gradlew assembleDebug` / `assembleRelease`. Assinatura do release lê `keystore.properties` (fora do git).
- GitHub Actions (`.github/workflows/android.yml`) roda testes e publica APKs como artefato a cada push.
- Distribuição por GitHub Releases (`dist/` local com `SHA256SUMS.txt`). A Google Play proíbe mineração no aparelho.

## 9. Decisões de projeto e limitações
- Kotlin puro para o hash: portável para qualquer ABI, ~100 a 400 kH/s por núcleo em celulares. Um motor NDK com extensões SHA-2 do ARMv8 seria 20x mais rápido e é a evolução natural.
- Um único endereço de recebimento: simples de auditar e é o que pools exigem. Reuso de endereço reduz privacidade.
- Gasto apenas desse endereço: se o usuário importar as palavras em outra carteira e receber em outros endereços da conta, o app não vê esses fundos (a outra carteira vê).
- Strings da JVM não podem ser zeradas de forma garantida: o app minimiza o tempo de vida das palavras e limpa campos ao sair.
