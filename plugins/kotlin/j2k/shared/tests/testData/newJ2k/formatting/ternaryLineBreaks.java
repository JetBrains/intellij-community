class J {
    String foo() {
        String s1 = bool()
                    ? "true"
                    : "false";

        String s2 = bool() ? "true" : "false";

        String s3 = bool() // condition
                    ? "true" // true
                    : "false"; // false

        String s4 = bool() ? "true"
                           : "false";

        String s5 =
                bool() ? "true" : "false";

        String s6 =
                bool()
                ? "true"
                : "false";

        String s7 =
                bool()
                ? "true" : "false";

        String s8 =
                bool() ? "true"
                       : "false";

        System.out.println(
                bool()
                ? "true" +
                  "true"
                : "false" + "false"
        );

        return bool()
               ? "true"
               : "false";
    }

    private boolean bool() {
        return false;
    }
}