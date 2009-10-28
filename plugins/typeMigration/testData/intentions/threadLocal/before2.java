// "Convert to ThreadLocal" "true"
class Test {
  String <caret>field="";
  void foo() {
    System.out.println(field);
  }
}