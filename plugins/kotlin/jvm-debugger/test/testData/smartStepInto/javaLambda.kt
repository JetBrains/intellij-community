fun foo() {
    <caret>listOf(1, 2, 3).stream().forEach { x ->
        println()
    }
}

// EXISTS: forEach: p0.invoke(), listOf(T), stream(), forEach(Consumer<? super T>)
