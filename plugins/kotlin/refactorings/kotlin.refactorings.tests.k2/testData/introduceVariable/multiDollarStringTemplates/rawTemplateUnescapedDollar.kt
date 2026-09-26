
fun foo(): String {
    return $$"""
        abc<selection>$def</selection>gh
    """.trimIndent()
}