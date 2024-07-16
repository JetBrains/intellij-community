public class J {
    void dfa(Object obj) {
        if (obj instanceof String) {
            // cast to not-null String, which is redundant altogether
            takesString((String) obj);
        }
    }

    void notNullVariable(Object o) {
        String s = (String) o;
        System.out.println(s.length());
    }

    void qualifiedCall(Object o) {
        if (o == null) return;
        int length = ((String) o).length();
    }

    private void takesString(String s) {
        System.out.println(s);
    }
}
