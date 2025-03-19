package to

import java.io.File


internal class JavaClass {
    fun foo(file: File?, target: MutableList<String?>?) {
        val list = ArrayList<String?>()
        if (file != null) {
            list.add(file.getName())
        }
        if (target != null) {
            target.addAll(list)
        }
    }
}
