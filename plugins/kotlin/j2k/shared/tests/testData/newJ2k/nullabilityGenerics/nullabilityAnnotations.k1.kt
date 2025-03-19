class J {
    private fun notNull1(strings: ArrayList<String>) {
    }

    private fun nullable2(strings: ArrayList<String?>) {
    }

    private fun notNull3(): ArrayList<String> {
        return ArrayList()
    }

    private fun nullable4(): ArrayList<String?> {
        return ArrayList()
    }
}
