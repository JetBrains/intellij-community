public class S {
    void f(Boolean override) {

        if (override == null) {
            //doSomething();
        } else if (override) {    // always false?
            //doOverride();
        }

    }
}
