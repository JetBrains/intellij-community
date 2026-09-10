interface Project
interface ProjectProvider {
    val project: Project
}

class FooBar(
    val project: Project
) {
    private fun foo() {
        val mockProvider = object : ProblemsProvider {
            override val project: Project = <selection>this@FooBar.project</selection>
        }
    }
}