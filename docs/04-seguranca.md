# Modelo de segurança e ameaças

## 1. Princípio

**As 24 palavras nunca ficam no aparelho.** Nem gravadas, nem cifradas, nem em backup, nem em área de transferência. O app guarda apenas dados públicos (endereço e zpub) e preferências. Esse princípio vale desde a criação da carteira até o envio de BTC.

## 2. O que fica no aparelho e como

| Dado | Onde | Proteção |
| --- | --- | --- |
| Endereço `bc1q...`, zpub, rótulo, data | Preferências do app | AES-256-GCM; chave gerada e mantida no Android Keystore (StrongBox se houver, senão TEE, senão software). A chave não pode ser exportada. |
| Pool, potência, temperaturas, bloqueio | Preferências do app | Mesmo mecanismo |
| 24 palavras, passphrase, chave privada | Nunca | Existem só em memória, por segundos, e são zeradas |

Backup em nuvem e transferência entre aparelhos estão desativados (`allowBackup=false`, regras de extração vazias).

## 3. Momentos em que o segredo existe em memória

1. **Criação**: entropia -> palavras -> endereço. As palavras ficam em memória enquanto a tela de exibição e a de confirmação estão abertas. Sair do app descarta tudo.
2. **Verificação** (Carteira > Verificar): as palavras digitadas são derivadas e comparadas; a tela limpa os campos ao sair.
3. **Envio**: as palavras digitadas derivam a chave do primeiro endereço; a chave assina e é zerada; a tela limpa os campos ao sair ou ao fechar.

Em todos os casos a janela está com `FLAG_SECURE`: sem capturas de tela, sem espelhamento, sem miniatura no seletor de apps.

## 4. Ameaças consideradas

| Ameaça | Mitigação | O que ainda é possível |
| --- | --- | --- |
| Celular perdido ou roubado | Não há segredo no aparelho; bloqueio biométrico/PIN opcional | O ladrão vê o endereço e o saldo público |
| Malware com acesso ao armazenamento | Dados cifrados com chave não exportável do Keystore | Nada de valor para roubar |
| Malware com root durante a criação ou o envio | Relatório de integridade alerta sobre root, depurador, emulador e ADB | Com root ativo, a memória pode ser lida; gere a carteira e envie em aparelho limpo |
| Troca do endereço de pagamento (clipper) | O endereço é exibido em destaque e pode ser verificado pelas palavras | Confira sempre antes de receber |
| Interceptação de rede | Stratum com TLS quando o pool oferece; HTTP em texto puro bloqueado; APIs só em HTTPS | Stratum sem TLS revela o endereço (público) e shares; não há segredo no tráfego |
| Pool ou API maliciosos | O app só envia endereço, shares e transações já assinadas | Um pool pode reter pagamentos; uma API pode mentir sobre saldo ou taxas, mas não gastar |
| Captura de tela / gravação | `FLAG_SECURE` nas telas sensíveis | Câmera externa apontada para a tela |
| Teclado malicioso | As palavras passam pelo teclado na verificação e no envio | Use o teclado padrão do sistema ou um de confiança |
| Backup em nuvem | Desativado | — |
| Engenharia social | O app nunca pede as palavras por mensagem, e-mail ou site | Ninguém que peça suas palavras é legítimo |

## 5. Fora do escopo

- Ataques físicos com extração de memória viva (cold boot) durante os segundos em que a chave existe.
- Vulnerabilidades do próprio Android ou do fabricante.
- Erros do usuário ao anotar as palavras: o quiz de 4 palavras reduz, mas não elimina. Use "Verificar minhas 24 palavras" após anotar.

## 6. Criptografia e verificação

Tudo é implementado no projeto, sem bibliotecas externas, e testado contra vetores oficiais:

- BIP39 (vetores Trezor), lista de palavras com SHA-256 conferido.
- BIP32 (vetor 1), BIP84 (`abandon` ×11 `about` -> `bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu`, zpub e fingerprint).
- RIPEMD-160 (vetores da especificação), SHA-256d (bloco gênesis e bloco 125552).
- Bech32/Bech32m (BIP173/BIP350) incluindo casos inválidos.
- ECDSA com RFC 6979 (4 vetores de referência) e o exemplo "Native P2WPKH" do BIP143 (sighash e assinatura byte a byte).

Isso não substitui auditoria externa. O código é aberto justamente para ser revisado.

## 7. Recomendações ao usuário

1. Anote as 24 palavras em papel, confira com "Verificar minhas 24 palavras" e guarde em lugar seguro. Considere duas cópias em lugares diferentes.
2. Ative o bloqueio do app (aba Segurança) e mantenha PIN/biometria no aparelho.
3. Mantenha o Android atualizado; evite aparelhos com root para operações com as palavras.
4. Antes de um envio grande, faça um envio de teste pequeno.
5. Para valores relevantes, transfira para uma carteira com hardware (Coldcard, Trezor, Ledger) ou uma carteira completa no PC.

## 8. Relatar vulnerabilidades

Abra uma issue no repositório com o prefixo `[seguranca]` ou envie mensagem para [@cavaleiro.ai](https://www.instagram.com/cavaleiro.ai/). Não publique detalhes exploráveis antes de uma correção.
