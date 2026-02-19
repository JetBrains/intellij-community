public class J {
    String test1(String s) {
        return (s != null) ? "true" : "false";
    }

    String test2(String s) {
        return (s
                !=
                null) ? "true" : "false";
    }

    String test3(String s) {
        return
                (s != null)
                ? "true"
                : "false";
    }
}