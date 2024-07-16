class Same {
    fun returnNull(): Same? {
        return null
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            var same: Same?
            same = Same().returnNull()

            var other: Other?
            other = Other().returnNull()

            val otherWithAssignment = Other().returnNull()
        }
    }
}
