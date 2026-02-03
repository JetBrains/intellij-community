// TODO: `name` parameter should be nullable in K2, looks like a bug in PsiClassType#getPsiContext()
object TestClass {
    private fun getCheckKey(category: String?, name: String, createWithProject: Boolean): String {
        return category + ':' + name + ':' + createWithProject
    }
}
