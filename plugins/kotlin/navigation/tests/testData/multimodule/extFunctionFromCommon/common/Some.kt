fun f(t: Throwable): String = t.stackTraceToSt<caret>ring()

// REF: (kotlin.stackTraceToString @ jar://kotlin-stdlib-common-sources.jar!/commonMain/kotlin/ExceptionsH.kt) @SinceKotlin("1.4") public expect fun Throwable.stackTraceToString(): String