class J {
    private class Marker {
        private val x = 42
    }

    fun foo(): Any {
        return Marker()
    }
}
