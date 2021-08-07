
class A {
    void toUpperCase() {
        String s = "kotlin";
        s.toUpperCase();
        s.toUpperCase(java.util.Locale.getDefault());
        s.toUpperCase(java.util.Locale.ROOT);
        s.toUpperCase(java.util.Locale.FRENCH);
        s.toUpperCase(java.util.Locale.US);
        s.toUpperCase(java.util.Locale.ENGLISH);
    }

    void toLowerCase() {
        String s = "kotlin";
        s.toLowerCase();
        s.toLowerCase(java.util.Locale.getDefault());
        s.toLowerCase(java.util.Locale.ROOT);
        s.toLowerCase(java.util.Locale.FRENCH);
        s.toLowerCase(java.util.Locale.US);
        s.toLowerCase(java.util.Locale.ENGLISH);
    }
}