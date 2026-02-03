// "Migrate unsupported yield syntax" "true"
// LANGUAGE_VERSION: 1.6

object yield {
    operator fun invoke(f: () -> Unit) = f()
}

fun test() {
    yie<caret>ld {  }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UnsupportedYieldFix