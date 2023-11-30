class FooWithField {
    @Jvm<caret>Field
    val i: Int = 4
}

// REF: (kotlin.jvm.JvmField) @Target(AnnotationTarget.FIELD) @Retention(AnnotationRetention.BINARY) @MustBeDocumented public actual annotation class JvmField
