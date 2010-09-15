public class X {
  void f(boolean isMale) {
    String title = isMale <caret>? "Mr." : "Ms.";
    System.out.println("title = " + title);
 }
}