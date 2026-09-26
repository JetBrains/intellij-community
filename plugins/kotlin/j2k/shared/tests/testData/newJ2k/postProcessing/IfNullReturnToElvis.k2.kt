import java.io.File

internal class C {
    fun foo(file: File): String {
        val parent = file.getParentFile()
        if (parent == null) return ""
        return parent.getName()
    }
}
