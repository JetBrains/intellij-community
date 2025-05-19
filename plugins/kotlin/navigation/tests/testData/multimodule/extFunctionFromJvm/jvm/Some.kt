fun f(t: Throwable): String = t.s<caret>tackTraceToString()

// REF: (kotlin.stackTraceToString @ jar://kotlin-stdlib-sources.jar!/jvmMain/kotlin/util/Exceptions.kt) @SinceKotlin("1.4") public actual fun Throwable.stackTraceToString(): String