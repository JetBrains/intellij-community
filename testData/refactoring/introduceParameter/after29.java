public class InnerOuter {
  String myField = "";

  interface Inner {
    void exec(final String myField);
  }

  Inner instance = new Inner() {
    public void exec(final String myField) {
      myField.charAt(1);
    }
  };

  void foo() {
   instance.exec(myField);
  }
}
