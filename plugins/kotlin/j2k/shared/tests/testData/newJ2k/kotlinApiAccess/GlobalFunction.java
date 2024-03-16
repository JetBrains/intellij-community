// IGNORE_K2
// !ADD_KOTLIN_API
import kotlinApi.KotlinApiKt;

class C {
    void foo() {
        String s = KotlinApiKt.globalFunction("x");
    }
}
