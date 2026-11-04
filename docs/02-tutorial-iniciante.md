# Tutorial para quem nunca fez isso: instalar e usar o Kabayero Miner

Este guia foi escrito para uma pessoa que nunca mexeu com Bitcoin, programação ou instalação de apps fora da loja. Siga na ordem. Cada passo diz exatamente onde clicar.

> **Aviso honesto, antes de tudo.** Minerar Bitcoin no celular não dá dinheiro. Um celular faz centenas de milhares de cálculos por segundo; a rede inteira faz centenas de quintilhões. O app existe para você aprender como a mineração e as carteiras funcionam, participar da rede e se divertir. Trate qualquer ganho como loteria.

## Parte 1: o que você precisa saber em 2 minutos

- **Bitcoin** é dinheiro digital. Ele fica registrado em uma lista pública (a blockchain) mantida por milhares de computadores.
- **Minerar** é usar o processador para tentar resolver um "quebra-cabeça" matemático. Quem resolve ganha bitcoins novos. É uma loteria: quanto mais poder de cálculo, mais bilhetes.
- **Carteira** é o que guarda o seu dinheiro. No Kabayero Miner ela é feita de **24 palavras**. Essas palavras são tudo: quem tem as palavras tem o dinheiro; quem perde as palavras perde o dinheiro. O app nunca as guarda, por isso **anote em papel**.
- **Endereço** é como um número de conta: começa com `bc1q`. Você pode mostrar para qualquer pessoa.
- **Pool** é um grupo de mineradores que divide os ganhos. **Solo** é minerar sozinho (ganha tudo ou nada).

## Parte 2: instalar o app no celular (o jeito mais fácil)

O app não está na Google Play (a loja não permite mineração). Você instala o arquivo `.apk` direto.

1. No celular, abra o navegador e acesse a página de downloads do projeto:
   `https://github.com/cavaleiro-ai/kabayero-miner/releases`
2. Toque na versão mais recente e, em "Assets", toque em **KabayeroMiner-1.0.0-release.apk** para baixar.
3. Quando terminar, toque na notificação de download (ou abra o app **Arquivos** e toque no arquivo).
4. O Android vai dizer algo como "Por segurança, seu telefone não tem permissão para instalar apps desconhecidos desta fonte". Toque em **Configurações**, ative **Permitir desta fonte** e volte.
5. Toque em **Instalar**. Se aparecer um aviso do Play Protect, toque em **Instalar mesmo assim** (o app é de código aberto; o aviso aparece para qualquer app fora da loja).
6. Toque em **Abrir**.

Se preferir baixar no computador e passar para o celular: baixe o `.apk` no PC, conecte o celular pelo cabo USB, copie o arquivo para a pasta **Download** do celular e, no celular, abra o app **Arquivos**, vá em Download e toque no arquivo. Também funciona enviar o arquivo para você mesmo pelo WhatsApp, Telegram ou Google Drive.

Como conferir que o arquivo é o original: na mesma página de downloads há um arquivo `SHA256SUMS.txt`. No PC, abra o PowerShell na pasta do download e digite `Get-FileHash .\KabayeroMiner-1.0.0-release.apk`. O código mostrado deve ser igual ao que está no `SHA256SUMS.txt`.

## Parte 3: primeira abertura e criação da carteira

1. Na tela inicial, leia o aviso e toque em **CRIAR CARTEIRA (24 PALAVRAS)**.
2. Na tela "Nova carteira", deixe a passphrase em branco (é um recurso avançado) e toque em **GERAR 24 PALAVRAS**.
3. Aparecem 24 palavras numeradas. **Pegue papel e caneta e copie as 24 palavras, na ordem, com atenção à grafia.** Não tire foto (a tela nem deixa), não mande por mensagem, não salve em arquivo.
4. Confira o que escreveu. Marque **Anotei as 24 palavras** e toque em **CONTINUAR PARA CONFIRMAÇÃO**.
5. O app pede 4 palavras específicas (por exemplo, a 3ª, a 11ª, a 17ª e a 24ª). Digite cada uma olhando no seu papel. Dê um nome para a carteira se quiser e toque em **CONFIRMAR E SALVAR**.
6. Pronto: você está no **Painel de mineração**. O endereço `bc1q...` aparece no topo.

Guarde o papel em um lugar seguro. Se o celular quebrar, for roubado ou você apagar o app, as 24 palavras recuperam tudo em qualquer carteira Bitcoin do mundo.

## Parte 4: começar a minerar

1. No painel, veja a **barra de potência**. Comece em 50%. Depois você ajusta: mais potência = mais calor e mais bateria.
2. Deixe o celular **conectado no carregador**. Por padrão o app só minera carregando (você pode mudar na aba Segurança).
3. Toque em **INICIAR MINERAÇÃO**.
4. Em alguns segundos o terminal (no fim da tela) mostra `tcp ok`, `worker autorizado` e `novo job`. O hashrate começa a subir.
5. Toque em **Config** e depois em **Permitir rodar em segundo plano** e confirme na tela do Android. Sem isso, alguns celulares matam a mineração quando a tela apaga.
6. Para parar, toque em **PARAR MINERAÇÃO** ou em **Parar** na notificação.

O que esperar: um share (prova de trabalho aceita) pode demorar minutos ou horas em celulares lentos. Isso é normal. O app sugere a dificuldade mais baixa possível ao servidor para você ver feedback mais cedo.

## Parte 5: escolher solo ou pool

Na aba **Config**:

- **SOLO** (padrão, `solo.ckpool.org`): ganha o bloco inteiro ou nada. É o modo "bilhete de loteria".
- **POOL** (`mine.ocean.xyz`): ganhos pequenos e proporcionais. Pagamentos só quando acumular o mínimo do pool.

Não precisa criar conta em nenhum deles: o app usa o seu endereço como usuário.

## Parte 6: proteger o celular do calor

Aba **Segurança**, painel **proteção térmica e bateria**:

- Veja a **temperatura atual da bateria**.
- Ajuste **Pausar a mineração a partir de** (sugestão: 42 a 45 °C).
- Ajuste **Retomar quando esfriar até** (sugestão: 36 a 38 °C).

Quando a bateria chega na temperatura de pausa, o app para de calcular e espera esfriar. Isso protege a bateria e o aparelho. Nunca minere com o celular dentro de bolso, capa grossa ou embaixo do travesseiro.

## Parte 7: receber bitcoin

1. Aba **Carteira**, toque em **RECEBER BTC**.
2. Mostre o QR para quem vai te pagar, ou toque em **Copiar endereço** e envie o endereço por mensagem.
3. Para acompanhar o saldo, na aba Carteira ative **Consultar saldo via mempool.space**. (Isso revela seu endereço ao serviço de consulta, que é público de qualquer forma.)

## Parte 8: enviar bitcoin

1. Aba **Carteira**, toque em **ENVIAR BTC**. O app consulta seu saldo e as taxas da rede.
2. Cole o **endereço de destino** (toque em **Colar** se ele estiver copiado). O app diz se o endereço é válido.
3. Digite o **valor em BTC** (exemplo: `0.0005`) ou toque em **MAX** para enviar tudo.
4. Escolha a **taxa**: "rápida" confirma em minutos; "econômica" pode levar horas, mas custa menos.
5. Toque em **REVISAR ENVIO** e confira tudo: destino, valor, taxa, troco e total.
6. Digite as **24 palavras** (do seu papel) e toque em **ASSINAR E TRANSMITIR**.
7. O app mostra o **txid** (o número da transação). Toque em **Ver no explorador** para acompanhar as confirmações.

As palavras são usadas só nesse momento e apagadas em seguida. Se errar uma palavra, o app avisa e não envia nada.

## Parte 9: rodar o app no computador (emulador)

Dá para testar o app no PC usando o emulador de Android que vem com o Android Studio. Não espere velocidade de mineração: o objetivo é só conhecer o app.

1. Baixe e instale o **Android Studio** em `https://developer.android.com/studio` (Windows, Mac ou Linux). Aceite as opções padrão; o instalador baixa tudo o que precisa (demora alguns minutos).
2. Abra o Android Studio. Na tela inicial, clique em **More Actions > Virtual Device Manager**.
3. Clique em **Create device**, escolha um telefone (ex.: Pixel 6), clique em **Next**, escolha uma imagem de sistema (ex.: API 34, clique em **Download** ao lado dela se pedir), **Next** e **Finish**.
4. Clique no botão de **play** ao lado do aparelho criado. Uma janela com um celular virtual abre.
5. Baixe o `KabayeroMiner-1.0.0-release.apk` no PC (Parte 2, passo 1) e **arraste o arquivo para cima da janela do celular virtual**. O app instala sozinho.
6. Abra o app na lista de apps do celular virtual e siga a Parte 3.

## Parte 10: compilar o app a partir do código (opcional, para curiosos)

Isso serve para quem quer ver ou modificar o código. Não é necessário para usar o app.

1. Instale o **Git** em `https://git-scm.com/downloads` (aceite os padrões).
2. Instale o **Android Studio** (Parte 9, passo 1). Ele já traz o Java e o SDK do Android.
3. Abra o Android Studio e clique em **Get from VCS** (ou **File > New > Project from Version Control**). Em URL, cole `https://github.com/cavaleiro-ai/kabayero-miner.git` e clique em **Clone**. Espere o Gradle terminar de sincronizar (barra de progresso no rodapé; a primeira vez baixa várias dependências).
4. No menu, clique em **Build > Build Bundle(s) / APK(s) > Build APK(s)**. Ao terminar aparece uma notificação com o link **locate**: o arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`.
5. Para instalar direto no seu celular pelo Android Studio: no celular, ative **Opções do desenvolvedor** (toque 7 vezes em "Número da versão" em Configurações > Sobre o telefone) e ligue **Depuração USB**; conecte pelo cabo; escolha o celular na barra superior do Android Studio e clique no **play** verde.

Pela linha de comando (PowerShell, dentro da pasta do projeto):

```powershell
.\gradlew.bat testDebugUnitTest     # roda os testes (todos devem passar)
.\gradlew.bat assembleDebug         # gera app/build/outputs/apk/debug/app-debug.apk
```

## Parte 11: problemas comuns

| Sintoma | O que fazer |
| --- | --- |
| "App não instalado" ou "pacote parece corrompido" | Baixe o arquivo de novo e confira o SHA-256. Se já existe uma versão antiga assinada com outra chave, desinstale-a primeiro. |
| Terminal mostra `falha em ...: ECONNREFUSED` e depois `tcp ok` | Normal: o app tentou IPv6, falhou, e conectou por IPv4. |
| Hashrate em 0 e status "aguardando carregador" | Conecte o carregador ou desligue "Minerar somente com carregador" na aba Segurança. |
| "Mineração pausada: temperatura ..." | O celular esquentou. Tire da capa, deixe esfriar, ou suba o limite na aba Segurança (com cuidado). |
| Mineração para quando a tela apaga | Config > Permitir rodar em segundo plano. Em Xiaomi/Huawei/Samsung, procure também "Bateria > Sem restrições" para o app. |
| Nunca aparece um share | Em celular lento isso pode levar horas. Em Config, "Sugerir dificuldade baixa" deve estar ligado; no ckpool, coloque `d=1` no campo senha. |
| Perdi as 24 palavras | Não existe recuperação. Se ainda tem saldo e o app está instalado, transfira tudo para uma carteira nova pelo botão Enviar (você precisa das palavras para isso, então sem elas não há como gastar). |
| Quero gastar em outra carteira | Importe as 24 palavras em Sparrow, BlueWallet ou Electrum escolhendo "SegWit nativo / BIP84". O primeiro endereço será o mesmo do app. |

## Parte 12: três regras de ouro

1. **As 24 palavras só existem no papel.** Nunca em foto, mensagem, nuvem ou arquivo.
2. **Confira o endereço** antes de receber e o **destino** antes de enviar. Bitcoin não tem estorno.
3. **Comece pequeno.** Antes de enviar um valor grande, faça um envio de teste com poucos satoshis.

Dúvidas e novidades: Instagram [@cavaleiro.ai](https://www.instagram.com/cavaleiro.ai/).
