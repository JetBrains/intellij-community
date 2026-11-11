// CHECK_ERRORS_AFTER
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
@file:JvmName("CompanionWithJavaUsage")

class Clash

companion fun Clash.out<caret>side(foo: String): Int = 42
