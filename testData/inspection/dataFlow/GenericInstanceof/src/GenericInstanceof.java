class Generic<T> {
  Generic() {}
}

class Test {
  void foo () {
    Generic g = new Generic ();
    if (g instanceof Generic<String>) {
      return;
    }
  }
}