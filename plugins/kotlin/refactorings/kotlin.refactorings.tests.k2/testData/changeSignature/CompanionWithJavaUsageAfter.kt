// CHECK_ERRORS_AFTER
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions
@file:JvmName("CompanionWithJavaUsage")

class Clash

companion fun Clash.outside(bar: String): Int = 42
