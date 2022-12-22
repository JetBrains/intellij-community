import java.lang.Thread

fun foo() {
    <caret>accessGetter(Thread::name)
}

fun accessGetter(f: (Thread) -> Any) {
    f(Thread())
}

// EXISTS: accessGetter((Thread) -> Any), getName(), getName()
// IGNORE_K2