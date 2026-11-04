# Kabayero Miner - regras R8
# Remove logs em release (reduz vazamento de informacoes)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
# ZXing usa reflexao minima; manter classes do core
-keep class com.google.zxing.** { *; }
# Mantem nomes de enum usados em serializacao de preferencias
-keepclassmembers enum br.com.cavaleiros.mineradores.** { *; }
