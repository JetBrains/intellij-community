class WithInvoke
operator fun WithInvoke.invoke(i: Int) {}

fun checkInvoke(w: WithInvoke) = w(42)