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
        String cString = null;
        if (bString.startsWith("b")) {
            aString = "bbbb";
            cString = "cccc";
        } else {
            aString = stringAssignment();
            cString = stringAssignment();
        }
    }
}