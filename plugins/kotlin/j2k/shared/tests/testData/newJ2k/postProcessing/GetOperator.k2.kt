import javaApi.JavaClass
import kotlinApi.KotlinClass

internal class X {
    fun get(index: Int): Int {
        return 0
    }
}

internal class C {
    fun foo(map: HashMap<String?, String?>): String? {
        return map.get("a")
    }

    fun foo(x: X): Int {
        return x.get(0)
    }

    fun foo(kotlinClass: KotlinClass): Int {
        return kotlinClass.get(0) // not operator!
    }

    fun foo(javaClass: JavaClass): Int {
        return javaClass.get(0)
    }
}
