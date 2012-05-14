class RearrangementTest12 {
  void method(int y) {
    x = y;
  }

  int x;

  Object convertX() {
    return new Integer(x);
  }

  Integer[] convertXs() {
    return new Integer[]{new Integer(x), new Integer(0)};
  }

  int getX() {
    return x;
  }

  String s;
}