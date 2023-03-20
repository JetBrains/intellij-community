import java.util.function.Function

class Test {
    private fun test() {
        bar(Foo::content)
    }

    private fun bar(mapper: Function<Foo, String>) {}
}
