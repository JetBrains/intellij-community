class BugTest {
    void <caret>f(String... s) {
        for (String s1 : s) {

        }
    }

    {
        f(new String[] {""});
    }
}