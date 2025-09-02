import kotlin.reflect.KProperty1

private fun isOptionalProperty(it: KProperty1<*, *>) = it.re<caret>turnType.isMarkedNullable

// REF: (kotlin.reflect.KCallable @ jar://kotlin-stdlib-sources.jar!/jvmMain/kotlin/reflect/KCallable.kt) public val returnType: KType