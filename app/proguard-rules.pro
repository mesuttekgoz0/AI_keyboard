# ProGuard kuralları — AIKeyboard2
# InputMethodService, Android sistem tarafından reflection ile çağrılır → sakla
-keep class com.fraunhofer.aikeyboard2.service.CustomKeyboardService { *; }

# ProfanityFilter ve WordRepository (object singletons)
-keep class com.fraunhofer.aikeyboard2.filter.ProfanityFilter { *; }
-keep class com.fraunhofer.aikeyboard2.data.WordRepository { *; }

# SharedPreferences key string'leri obfuscate edilmesin
-keepclassmembers class com.fraunhofer.aikeyboard2.data.WordRepository {
    private static final java.lang.String *;
}

# Genel Android standart kurallar
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
