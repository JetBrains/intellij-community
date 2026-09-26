package smartStepIntoGenericWithValueClassAsParameter

private fun mulBy2(x: UInt) = x + x

fun main() {
	foo(::mulBy2)
}

private fun foo(func: (UInt) -> UInt) {
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
	use(func(29.toUInt()))
}

private fun use(x: UInt) {}
