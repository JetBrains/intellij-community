public class InnerOuter {
  String myField = "";

  interface Inner {
    void exec();
  }

  Inner instance = new Inner() {
    public void exec() {
      <selection>myField</selection>.charAt(1);
    }
  };

  void foo() {
   instance.exec();
  }
}
