public class Test {
  String foo(String s, int i) {
     return newMethod(s.substring(i) + s.substring(i) + i);
  }

    private String newMethod(String s) {
        return s;
    }
}