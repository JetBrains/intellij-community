class IDEADEV10489 {
    static String getS() {
        return null;
    }

    static void f() {
        String s = getS();
        if (s != null) s.length();
        if (foo()) {
          int[] i1 = new int [s.length()];
        } else if (foo()) {
          int[] i2 = new int [] {s.length()};
        } else if (foo()) {
          int[][] i3 = new int [(s = "").length()][s.length()];
        } else{
          int[] i4 = new int[] {(s = "").length(), s.length()};
        }
    }

    private static boolean foo() {
      return false;
    }
}