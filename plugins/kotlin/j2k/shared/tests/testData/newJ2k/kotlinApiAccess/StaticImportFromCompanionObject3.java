// !ADD_KOTLIN_API
import static kotlinApi.KotlinClass.getStaticProperty;

class C {
    int foo() {
        return getStaticProperty();
    }
}
