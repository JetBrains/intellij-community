package dependency

public class Foo<T> {
}

public class FooIterator<T> {
}

public operator fun <T> Foo<T>.iterator(): FooIterator<T> = FooIterator<T>()
public operator fun <T> FooIterator<T>.hasNext(): Boolean = false
public operator fun <T> FooIterator<T>.next(): T = throw IllegalStateException()
