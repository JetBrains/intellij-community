import java.io.File

internal class C {
    fun foo(file: File?) {
        if (file != null) {
            file.delete()
        }
    }
}
