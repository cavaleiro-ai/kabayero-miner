# Perguntas frequentes

### Vou ganhar dinheiro minerando no celular?
Não. Um celular faz centenas de milhares de hashes por segundo; a rede faz centenas de quintilhões. Em solo, o painel mostra uma expectativa de bilhões de anos para um bloco. Em pool, frações de centavo por dia, abaixo do mínimo de pagamento da maioria dos pools. O app é para aprender, participar e se divertir.

### Então por que existe o modo solo?
Porque é possível, ainda que improvável, achar um bloco: é uma loteria com bilhete grátis. Em 2022 e 2023 mineradores solo com hardware pequeno acharam blocos. O app mostra a chance real para você decidir.

### Onde ficam as minhas 24 palavras?
Só no papel onde você anotou. O app nunca as grava. Se perder o papel, perde o acesso ao dinheiro. Se perder o celular, as palavras recuperam tudo em qualquer carteira Bitcoin.

### O app pede as palavras para enviar. Isso é seguro?
É o preço de não guardá-las. As palavras ficam em memória por alguns segundos, com captura de tela bloqueada, e são apagadas depois de assinar. Em aparelho com root ou malware, qualquer app que manipule a chave está exposto; por isso o app avisa sobre root e depurador na aba Segurança.

### Posso usar o app só como carteira, sem minerar?
Sim. Crie a carteira e use Receber/Enviar. A mineração só roda se você tocar em Iniciar.

### Posso minerar para um endereço de outra carteira?
Sim: "Usar endereço que já tenho". Nesse caso o app não consegue enviar (não tem as palavras); gaste pela carteira original.

### Usei uma passphrase ao criar. E agora?
A passphrase faz parte da carteira: palavras + passphrase geram outro endereço. Guarde-a junto com as palavras e informe-a ao verificar e ao enviar. Sem ela, o endereço derivado será outro e o app recusará assinar.

### O que é a zpub?
Uma chave pública estendida da sua conta. Ela permite que outra carteira (Sparrow, BlueWallet, Electrum) acompanhe o saldo e gere endereços de recebimento, sem poder gastar. Revela todos os seus endereços; compartilhe com cuidado.

### Por que o app usa só um endereço?
Pools pagam para um endereço fixo, e um endereço único é simples de auditar. Reutilizar endereço reduz a privacidade, não a segurança.

### O app está esquentando o celular. É normal?
Sim, mineração usa o processador no máximo. Use a barra de potência (50% é um bom começo) e os limites térmicos na aba Segurança. Nunca minere dentro de bolso, capa grossa ou abaixo de travesseiro.

### A mineração para quando a tela desliga.
Toque em Config > Permitir rodar em segundo plano e aceite. Em Xiaomi, Huawei, Samsung e outros, procure também "Bateria > Sem restrições" para o app. O serviço em primeiro plano (notificação fixa) é obrigatório e não pode ser removido enquanto minera.

### O terminal mostra "falha em 2001:...: ECONNREFUSED" e depois "tcp ok".
Sua operadora entrega IPv6 e o pool não aceita por ele. O app tenta IPv4 em seguida. Não é erro.

### Nunca vi um share aceito.
Em hardware lento um share pode levar horas. Confirme em Config que "Sugerir dificuldade baixa" está ligado. No ckpool, coloque `d=1` no campo senha do worker. Shares não mudam sua chance de bloco; são só feedback.

### Posso usar outro pool?
Sim: Config > servidor Stratum > Personalizado, com host, porta e TLS. O usuário será `seuendereco.worker`. Pools que exigem cadastro e usuário próprio não funcionam com esse formato.

### O app usa internet para quê?
Para falar com o pool (Stratum) e, se você pedir, para consultar saldo, taxas e transmitir transações no mempool.space. Nada mais. Não há telemetria.

### O app está na Google Play?
Não. A Play proíbe apps que mineram no aparelho. A distribuição é pelo GitHub Releases. Confira o SHA-256 do arquivo.

### Como atualizo?
Baixe o novo APK e instale por cima. Como é assinado com a mesma chave, o Android mantém seus dados. Se um dia a chave mudar, será preciso desinstalar (e seus dados públicos serão apagados; as palavras no papel continuam valendo).

### Perdi o celular. E o dinheiro?
Está na blockchain, protegido pelas 24 palavras. Instale o app em outro aparelho e use "Usar endereço que já tenho" para continuar minerando, ou importe as palavras em uma carteira completa para gastar.

### O que o RAG local faz?
É uma ferramenta de linha de comando (`tools/rag`) que indexa esta documentação e o código no seu computador e responde perguntas offline. Veja [tools/rag/README.md](../tools/rag/README.md).
