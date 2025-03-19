class FooWithField {
    @Jvm<caret>Field
    val i: Int = 4
}

// REF: (kotlin.jvm.JvmField @ jar://kotlin-stdlib-sources.jar!/jvmMain/kotlin/jvm/annotations/JvmPlatformAnnotations.kt) @Target(AnnotationTarget.FIELD) @Retention(AnnotationRetention.BINARY) @MustBeDocumented public actual annotation class JvmField
