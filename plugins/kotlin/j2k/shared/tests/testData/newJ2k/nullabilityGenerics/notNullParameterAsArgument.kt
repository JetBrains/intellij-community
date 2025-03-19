internal class TypeArgumentOnly {
    private fun notNull(strings: ArrayList<String>?) {
        foo(strings)
    }

    private fun foo(strings: ArrayList<String>?) {
    }
}

internal class DeeplyNotNull {
    private fun notNull(strings: ArrayList<String>) {
        foo(strings)
    }

    // top-level ArrayList can still be nullable
    private fun foo(strings: ArrayList<String>?) {
    }
}
