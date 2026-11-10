import java.util.function.Supplier

internal class A {
    fun lambda(items: MutableList<String>) {
        val supplier: Supplier<MutableList<String>?> = Supplier { items }
        supplier.get()!!.add("a")
    }

    fun blockLambda(items: MutableList<String>) {
        val supplier: Supplier<MutableList<String>?> = Supplier { items }
        supplier.get()!!.add("a")
    }

    fun libraryLambda(items: MutableList<String?>?) {
        ThreadLocal.withInitial<MutableList<String?>?>(Supplier { items })
    }

    fun switchResult(k: Int, first: MutableList<String?>?, second: MutableList<String?>?): MutableList<String?>? {
        return when (k) {
            0 -> first
            else -> second
        }
    }
}
