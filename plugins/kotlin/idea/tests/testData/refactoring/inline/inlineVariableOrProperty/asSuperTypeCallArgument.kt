fun mmm(str: String) {
    open class KotlinChangeSignatureProcessor(s: String, ss: String)

    val proje<caret>ct = str.substring(1)
    val p = object : KotlinChangeSignatureProcessor(project, "") {
        override fun equals(other: Any?): Boolean {
            return super.equals(other)
        }
    }
}