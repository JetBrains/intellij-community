class RearrangementTest12 {
  int    x;
  String s;

  void method(int y) {
    x = y;
  }

  int getX() {
    return x;
  }

  Object convertX() {
    return new Integer(x);
  }

  Integer[] convertXs() {
    return new Integer[]{new Integer(x), new Integer(0)};
  }
}