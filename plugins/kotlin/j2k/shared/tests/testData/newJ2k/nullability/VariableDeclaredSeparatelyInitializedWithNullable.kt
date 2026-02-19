class Same {
    fun returnNull(): Same? {
        return null
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val same: Same?
            same = Same().returnNull()

            val other: Other?
            other = Other().returnNull()

            val otherWithAssignment = Other().returnNull()
        }
    }
}
