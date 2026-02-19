class J {
    object Z {
        internal const val MAGIC: Int = 42
    }

    inner class A {
        fun foo(): Int {
            return Z.MAGIC
        }
    }
}
