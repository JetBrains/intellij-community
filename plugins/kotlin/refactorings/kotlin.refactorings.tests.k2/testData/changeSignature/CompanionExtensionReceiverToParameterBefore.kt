// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Example

companion fun Example.rep<caret>eatText(): String {
    return "extension".repeatLocal(3)
}

fun String.repeatLocal(count: Int): String = repeat(count)
