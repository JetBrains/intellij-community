// "Migrate unsupported yield syntax" "true"
// LANGUAGE_VERSION: 1.6

object yield {
    operator fun invoke(f: () -> Unit) = f()
}

fun test() {
    yie<caret>ld {  }
}
