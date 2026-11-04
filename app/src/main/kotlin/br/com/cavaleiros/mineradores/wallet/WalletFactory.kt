package br.com.cavaleiros.mineradores.wallet

/**
 * Resultado da geracao/derivacao de uma carteira.
 *
 * [words] existe apenas em memoria durante o fluxo de criacao; o app persiste
 * somente [address] e [zpub] (dados publicos, incapazes de gastar fundos).
 */
class GeneratedWallet(
    val words: List<String>,
    val address: String,
    val zpub: String,
    val masterFingerprint: String,
    val derivationPath: String
)

/**
 * Orquestra BIP39 -> BIP32 -> BIP84 (SegWit nativo, enderecos bc1q...).
 * Todo material secreto (entropia, semente, chaves privadas) e zerado apos o uso.
 */
object WalletFactory {
    const val ACCOUNT_PATH = "m/84'/0'/0'"
    const val FIRST_ADDRESS_PATH = "m/84'/0'/0'/0/0"

    fun create(bip39: Bip39, passphrase: String = ""): GeneratedWallet {
        val entropy = bip39.generateEntropy(256)
        try {
            val words = bip39.entropyToMnemonic(entropy)
            return fromMnemonic(bip39, words, passphrase)
        } finally {
            entropy.fill(0)
        }
    }

    /**
     * Deriva endereco e zpub a partir de uma frase existente.
     * @throws IllegalArgumentException se a frase for invalida (checksum).
     */
    fun fromMnemonic(bip39: Bip39, words: List<String>, passphrase: String = ""): GeneratedWallet {
        val normalized = words.map { Bip39.normalizeWord(it) }
        require(bip39.isValid(normalized)) { "frase mnemonica invalida (checksum)" }
        val seed = bip39.toSeed(normalized, passphrase)
        try {
            val master = Bip32.masterFromSeed(seed)
            try {
                val masterFp = Hash.hex(Hash.hash160(master.publicKey()).copyOfRange(0, 4))
                val account = Bip32.derivePath(master, ACCOUNT_PATH)
                try {
                    val zpub = account.neutered().serialize(Bip32.ZPUB)
                    val external = account.deriveChild(0)
                    val first = external.deriveChild(0)
                    val address = Bech32.encodeSegwit("bc", 0, Hash.hash160(first.publicKey()))
                    external.wipe()
                    first.wipe()
                    return GeneratedWallet(normalized, address, zpub, masterFp, FIRST_ADDRESS_PATH)
                } finally {
                    account.wipe()
                }
            } finally {
                master.wipe()
            }
        } finally {
            seed.fill(0)
        }
    }
}

/**
 * Chave de gasto do primeiro endereco (m/84'/0'/0'/0/0). Existe apenas em memoria
 * durante a assinatura de um envio; chame [wipe] logo depois.
 */
class SpendingKey(val privateKey: ByteArray, val publicKey: ByteArray, val address: String) {
    fun wipe() {
        privateKey.fill(0)
    }
}

object SpendingKeyFactory {
    /** Deriva a chave do primeiro endereco a partir das palavras (e passphrase). */
    fun fromMnemonic(bip39: Bip39, words: List<String>, passphrase: String = ""): SpendingKey {
        val normalized = words.map { Bip39.normalizeWord(it) }
        require(bip39.isValid(normalized)) { "frase mnemonica invalida (checksum)" }
        val seed = bip39.toSeed(normalized, passphrase)
        try {
            val master = Bip32.masterFromSeed(seed)
            try {
                val first = Bip32.derivePath(master, WalletFactory.FIRST_ADDRESS_PATH)
                try {
                    val pub = first.publicKey()
                    val address = Bech32.encodeSegwit("bc", 0, Hash.hash160(pub))
                    return SpendingKey(first.key.copyOf(), pub, address)
                } finally {
                    first.wipe()
                }
            } finally {
                master.wipe()
            }
        } finally {
            seed.fill(0)
        }
    }
}

/** Validacao de enderecos Bitcoin mainnet para a opcao "usar endereco existente". */
object AddressValidator {
    enum class Type(val label: String) {
        P2PKH("Legado (P2PKH)"),
        P2SH("Script / SegWit encapsulado (P2SH)"),
        P2WPKH("SegWit nativo (P2WPKH)"),
        P2WSH("SegWit nativo script (P2WSH)"),
        P2TR("Taproot (P2TR)"),
        FUTURE_SEGWIT("SegWit versao futura")
    }

    class Result(val valid: Boolean, val type: Type?, val normalized: String, val message: String)

    fun validate(input: String): Result {
        val addr = input.trim()
        if (addr.isEmpty()) return Result(false, null, addr, "Informe um endereco")
        val lower = addr.lowercase()
        if (lower.startsWith("tb1") || addr.startsWith("m") || addr.startsWith("n") || addr.startsWith("2")) {
            return Result(false, null, addr, "Endereco de testnet nao e aceito")
        }
        if (lower.startsWith("bc1")) {
            val seg = Bech32.decodeSegwit("bc", addr)
                ?: return Result(false, null, addr, "Endereco bech32 invalido (checksum)")
            val type = when {
                seg.version == 0 && seg.program.size == 20 -> Type.P2WPKH
                seg.version == 0 && seg.program.size == 32 -> Type.P2WSH
                seg.version == 1 && seg.program.size == 32 -> Type.P2TR
                else -> Type.FUTURE_SEGWIT
            }
            return Result(true, type, lower, "Endereco valido: ${type.label}")
        }
        val payload = Base58.decodeCheck(addr)
            ?: return Result(false, null, addr, "Endereco invalido (checksum Base58)")
        if (payload.size != 21) return Result(false, null, addr, "Tamanho de endereco invalido")
        return when (payload[0].toInt() and 0xFF) {
            0x00 -> Result(true, Type.P2PKH, addr, "Endereco valido: ${Type.P2PKH.label}")
            0x05 -> Result(true, Type.P2SH, addr, "Endereco valido: ${Type.P2SH.label}")
            else -> Result(false, null, addr, "Prefixo de endereco desconhecido")
        }
    }
}
