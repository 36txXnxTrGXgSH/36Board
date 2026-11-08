# 36Board — règles de réduction (R8) pour la version release.

# Le service de saisie est instancié par le système à partir de son nom :
# R8 ne peut pas voir cette référence, il faut la conserver explicitement.
-keep class com.board36.app.ime.Board36Service { *; }

# Idem pour l'activité de réglages, référencée par le manifeste et par method.xml.
-keep class com.board36.app.ui.MainActivity { *; }

# Les vues du clavier sont construites en Kotlin (aucun XML), R8 les suit tout seul.
# On garde seulement les constructeurs (Context) au cas où une vue serait un jour inflatée.
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Traces d'exception lisibles en cas de rapport de bug.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
