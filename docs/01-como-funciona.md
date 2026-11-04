# Como o Kabayero Miner funciona, passo a passo

Este documento explica o que acontece dentro do app em cada etapa, em linguagem simples. Para instalar e usar pela primeira vez, veja o [tutorial para iniciantes](02-tutorial-iniciante.md). Para detalhes de engenharia, veja a [arquitetura](03-arquitetura.md).

## 1. Visão geral

O Kabayero Miner faz três coisas:

1. **Cria uma carteira Bitcoin** de 24 palavras no seu celular e mostra o endereço para receber.
2. **Minera Bitcoin** com o processador do celular, sozinho (solo) ou em grupo (pool), enviando as recompensas para esse endereço.
3. **Recebe e envia BTC** desse endereço, sem nunca guardar as 24 palavras no aparelho.

Tudo roda no celular. O app não tem servidor próprio, não pede cadastro e não coleta dados.

## 2. Primeira abertura

Ao abrir pela primeira vez você vê a tela inicial com duas opções:

- **Criar carteira (24 palavras)**: o app gera uma carteira nova.
- **Usar endereço que já tenho**: você cola um endereço Bitcoin de outra carteira e o app apenas minera para ele (não consegue enviar, porque não tem as palavras).

## 3. Criação da carteira (o que acontece por dentro)

1. O app pede ao Android 256 bits de aleatoriedade segura (`SecureRandom`).
2. Esses bits viram **24 palavras** da lista oficial BIP39 (2048 palavras em inglês). As 24 palavras são a sua carteira inteira: quem tem as palavras tem o dinheiro.
3. Das palavras o app deriva, pelos padrões BIP32/BIP84, o **primeiro endereço de recebimento** no caminho `m/84'/0'/0'/0/0`. Ele começa com `bc1q`.
4. Também deriva a **zpub**, uma chave pública estendida que permite acompanhar o saldo em outra carteira (somente leitura).
5. As palavras aparecem **uma única vez**, em uma tela que bloqueia capturas de tela. Você anota em papel.
6. O app pede 4 palavras aleatórias de volta para ter certeza de que você anotou certo.
7. Ao confirmar, o app grava **apenas** o endereço e a zpub, cifrados com uma chave que vive no hardware seguro do aparelho (Android Keystore). As 24 palavras são descartadas da memória e nunca são gravadas.

Se você sair do app com as palavras na tela, a carteira pendente é descartada e você gera outra.

## 4. O painel de mineração

Depois de criar a carteira, o app abre o painel. Nele você vê:

- **Hashrate**: quantos cálculos por segundo o celular está fazendo (kH/s = mil por segundo).
- **Shares aceitos / rejeitados**: cada "share" é uma prova de trabalho enviada ao servidor. É o jeito de o servidor saber que você está trabalhando.
- **Blocos**: quantos blocos você encontrou (em solo, é raríssimo).
- **Barra de potência**: de 0 a 100% da capacidade do celular. O app traduz a porcentagem em número de threads e intensidade. Exemplo em 4 núcleos: 50% = 2 threads a 100%. Mudar a barra vale na hora.
- **Botão Iniciar / Parar mineração**.
- **Rede e trabalho**: modo, bloco atual da rede, dificuldade, job atual.
- **Expectativa honesta**: quanto tempo, na média, o seu celular levaria para achar um bloco sozinho. O número é enorme de propósito; o app não promete renda.
- **Terminal**: registro do que acontece (conexão, autorização, jobs, shares).

## 5. O que acontece quando você toca em Iniciar

1. O app inicia um **serviço em primeiro plano** (aquela notificação fixa). Isso diz ao Android para não matar o processo e mantém o processador acordado (wake lock).
2. O serviço abre uma conexão TCP (ou TLS) com o servidor escolhido usando o protocolo **Stratum V1**, o mesmo usado por mineradoras profissionais.
3. O app se apresenta (`mining.subscribe`), se autoriza com `seuendereco.nomedoworker` (`mining.authorize`) e sugere uma dificuldade baixa para receber feedback rápido.
4. O servidor envia um **job**: os pedaços do próximo bloco candidato. O app monta o cabeçalho do bloco e começa a testar números (nonces) calculando SHA-256 duas vezes por tentativa.
5. Cada thread trabalha em uma faixa própria de números, sem repetir trabalho. A intensidade controla pausas curtas entre lotes para segurar a temperatura.
6. Quando um resultado é bom o suficiente para a dificuldade do servidor, o app envia um **share**. Se for bom o suficiente para a dificuldade da rede inteira, é um **bloco**, e a recompensa vai para o seu endereço.
7. A cada 30 segundos (ou quando a rede acha um bloco) o servidor manda um job novo.

## 6. Solo ou pool

- **Solo** (`solo.ckpool.org` ou `public-pool.io`): você compete sozinho. Se achar um bloco, a recompensa inteira (cerca de 3,125 BTC mais taxas, menos a taxa do servidor) é sua. A chance é minúscula; o painel mostra a expectativa.
- **Pool** (`mine.ocean.xyz`): você soma forças com milhares de pessoas e recebe uma fração proporcional ao trabalho. Em celular isso dá frações de centavo por dia.
- **Personalizado**: qualquer servidor Stratum V1, com TLS opcional.

Em nenhum dos casos é preciso criar conta: o seu endereço Bitcoin é o seu login.

## 7. Proteções automáticas

Na aba **Segurança**, o painel "proteção térmica e bateria" tem:

- **Temperatura atual da bateria**, atualizada a cada 5 segundos.
- **Pausar a mineração a partir de X °C**: ao chegar nessa temperatura o app para de calcular, mas mantém a conexão.
- **Retomar quando esfriar até Y °C**: a mineração só volta quando a bateria esfriar até esse valor. A diferença entre X e Y evita liga-desliga constante.
- **Minerar somente com carregador** e **pausar abaixo de N% de bateria**.
- **Permitir rodar em segundo plano**: abre a tela do Android para isentar o app da otimização de bateria.

O serviço relê essas configurações a cada 5 segundos, então mudanças valem com a mineração ligada.

## 8. Receber BTC

Na aba **Carteira**, toque em **Receber BTC**. Aparece o QR code e o endereço. Quem quiser te pagar escaneia o QR ou cola o endereço. As recompensas do pool chegam no mesmo endereço.

## 9. Enviar BTC

Na aba **Carteira**, toque em **Enviar BTC**:

1. O app consulta no mempool.space o seu saldo disponível (saídas não gastas), as taxas recomendadas e a altura atual da rede.
2. Você informa o endereço de destino (o app valida o formato), o valor em BTC (ou **MAX** para enviar tudo) e escolhe a taxa (rápida, 30 min, 1 hora, econômica ou manual em sat/vB).
3. O app monta o plano: quais saídas usar, a taxa exata em satoshis, o troco (que volta para o seu próprio endereço) e o total debitado. Você revisa.
4. Você digita as **24 palavras** (e a passphrase, se usou). O app deriva a chave privada em memória, confere que ela gera o endereço salvo, assina a transação e transmite.
5. A chave e as palavras são apagadas da memória. O app mostra o txid com um link para o explorador.

A tela de envio bloqueia capturas de tela e limpa os campos se o app for para segundo plano. Nada é gravado em nenhum momento.

## 10. Verificar as palavras

Em **Carteira > Verificar minhas 24 palavras** você digita a frase e o app apenas confirma se ela gera o endereço salvo. Útil para testar o papel onde anotou. Nada é gravado.

## 11. Trocar ou apagar

- **Trocar carteira / endereço**: remove o endereço atual do aparelho. Seus fundos continuam existindo, porque pertencem às 24 palavras, não ao app.
- **Apagar todos os dados do app** (aba Segurança, zona de perigo): remove endereço, configurações e a chave do Keystore.

## 12. Resumo do que fica no aparelho

| Fica guardado | Nunca fica guardado |
| --- | --- |
| Endereço `bc1q...` | As 24 palavras |
| zpub (chave pública estendida) | A passphrase |
| Preferências (pool, potência, temperaturas) | A chave privada |
| Tudo isso cifrado com AES-256-GCM via Android Keystore | |
