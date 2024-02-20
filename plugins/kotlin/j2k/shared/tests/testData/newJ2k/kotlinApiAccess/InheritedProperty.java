// IGNORE_K2
import kotlinApi.*

class C extends KotlinClass {
    void foo() {
        System.out.println(getProperty());
        setProperty("a")
    }
}