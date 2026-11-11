// CHECK_ERRORS_AFTER
// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Bar {
    void foo() {
        CompanionWithJavaUsage.outside("foo")
    }
}