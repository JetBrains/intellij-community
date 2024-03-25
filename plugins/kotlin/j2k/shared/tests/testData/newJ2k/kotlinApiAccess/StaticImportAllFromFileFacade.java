// !ADD_KOTLIN_API
import static kotlinApi.KotlinApiKt.*;

class C {
    int foo() {
        setExtensionProperty("a", 1);
        return getExtensionProperty("b");
    }
}
