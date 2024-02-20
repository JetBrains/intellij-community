// IGNORE_K2
import kotlinApi.*

class A {
    int foo(KotlinInterface t) {
        return t.nullableFun().length() + t.notNullableFun().length();
    }
}