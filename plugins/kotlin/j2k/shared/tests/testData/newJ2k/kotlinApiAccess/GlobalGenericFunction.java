// IGNORE_K2
import kotlinApi.KotlinApiKt;

class C {
    void foo() {
        int v = KotlinApiKt.<Integer>globalGenericFunction(1);
    }
}
