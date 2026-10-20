// COMPILER_ARGUMENTS: -XXLanguage:+CompanionBlocks -XXLanguage:+CompanionExtensions

class Label {
    companion {
        fun inBlock(): String = render("b")
    }
}

companion fun Label.<caret>render(prefix: String): String = "$prefix!"

fun outside(): String = Label.render("p")
