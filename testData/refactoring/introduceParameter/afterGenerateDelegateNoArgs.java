class Test {
    public int m() {
        return m(0);
    }

    public int m(int anObject) {
        return anObject;
    }
}

class Test1 {
  Test t;

  public int n(int v) {
    return t.m();
  }
}