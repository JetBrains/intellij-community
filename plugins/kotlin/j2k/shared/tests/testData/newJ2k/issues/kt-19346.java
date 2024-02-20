// IGNORE_K2
package test;

public class TestAssignmentInReturn {
    private String last;

    public String foo(String s) {
        return last = s;
    }
}