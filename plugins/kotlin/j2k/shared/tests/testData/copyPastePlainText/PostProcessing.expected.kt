package to

import java.io.File


internal class JavaClass {
    fun foo(file: File?, target: MutableList<String>?) {
        val list = ArrayList<String>()
        if (file != null) {
            list.add(file.name)
        }
        target?.addAll(list)
    }
}
