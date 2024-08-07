// ERROR: Cannot access 'val MAGIC: Int': it is private in '/J.Z'.
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
