package test

class X(val v: Long) {
    private fun create() {
        newInstance {
            this.value = v
        }
    }

    override fun toString(): String {
        return v.toString()
    }
}

private fun newInstance (init: Immutable.Builder.() -> Unit) {
}