class Library {
  void call() {}

  String string() { return ""; }
}

class User {
  void main() {
    Library lib = new Library();
    lib.call();
    lib.string().isEmpty();

    new Library().call();
    new Library().string().isEmpty();
  }
}