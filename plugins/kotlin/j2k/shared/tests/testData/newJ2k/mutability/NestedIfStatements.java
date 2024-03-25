// IGNORE_K2

import org.jetbrains.annotations.*;

class A {

    public String stringAssignment() {
        if (Math.random() > 0.50) {
            return "a string";
        }
        return "b string";
    }

    public void aVoid() {
        String aString;
        String bString = stringAssignment();
        String cString;
        if (stringAssignment().startsWith("a")) {
            aString = stringAssignment();
            cString = "aaaa";
            if (aString == bString) {
                aString = stringAssignment();
            }
        } else if (stringAssignment().startsWith("b")) {
            aString = "bbbb";
            cString = "bbbb";
        } else {
            aString = "cccc"
            cString = "cccc"
    }
}