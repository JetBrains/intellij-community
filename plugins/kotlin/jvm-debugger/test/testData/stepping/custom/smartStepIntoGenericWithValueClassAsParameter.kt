package smartStepIntoGenericWithValueClassAsParameter

fun main() {
	generic(UIntGroup(), 37u)
	exact(UIntGroup(), 37u)
}

private fun <N> generic(g: Group<N>, x: N) {
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
	//Breakpoint!
	val y = g.plus(x, g.zero())
}

private fun exact(g: Group<UInt>, x: UInt) {
    // SMART_STEP_INTO_BY_INDEX: 1
    // RESUME: 1
	//Breakpoint!
    val y = g.plus(x, g.zero())
}

private abstract class Group<N> {
    abstract fun zero(): N
	abstract fun plus(x: N, y: N): N
}

private class UIntGroup: Group<UInt>() {
    override fun zero(): UInt = 0u
	override fun plus(x: UInt, y: UInt): UInt = x + y
}
