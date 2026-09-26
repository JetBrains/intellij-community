package test

class X(val value: Long) {
    private fun create() {
        newInstance {
            this.value = this@X.value
        }
    }

    override fun toString(): String {
        return value.toString()
    }
}

private fun newInstance (init: Immutable.Builder.() -> Unit) {
}