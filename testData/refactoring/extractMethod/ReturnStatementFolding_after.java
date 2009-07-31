public class Test {
  String foo(String[] s, int i) {
     return newMethod(s[i]);
  }

    private String newMethod(String s) {
        return s;
    }
}