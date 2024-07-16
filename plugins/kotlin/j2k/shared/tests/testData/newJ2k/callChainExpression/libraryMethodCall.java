class Library {
  static void call() {}

  static String string() { return ""; }
}

class User {
  void main() {
    Library.call();
    Library.string().isEmpty();
  }
}