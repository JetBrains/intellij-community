class WithInvoke
operator fun WithInvoke.invoke() {}

fun checkInvoke(w: WithInvoke) = w(<caret>)