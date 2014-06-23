// "Convert to ThreadLocal" "true"
class Test {
  static final Integer <caret>field;
  static {
    field = new Integer(0);
  }
}