class List<T> {
  public void add(T t) {}
}

class Test {
    void f() {
        List foo = new List();
        foo.add(3);
        foo.add("bar");
    }
}
