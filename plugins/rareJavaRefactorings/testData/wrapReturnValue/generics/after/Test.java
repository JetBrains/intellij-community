class Test<T> {
  T myT;

    Wrapper<T> foo() {
    return new Wrapper<T>(myT);
  }
}