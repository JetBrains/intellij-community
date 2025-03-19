// !ADD_KOTLIN_API
import kotlinApi.*

class C extends KotlinClass {
    void foo() {
        System.out.println(getProperty());
        setProperty("a")
    }
}