// ERROR: INVISIBLE_REFERENCE
class J {
    object Z {
        private const val MAGIC = 42
    }

    inner class A {
        fun foo(): Int {
            return Z.MAGIC
        }
    }
}
