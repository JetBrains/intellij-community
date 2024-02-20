// IGNORE_K2
class Library {
  final public String myString;
}

class User {
  void main() {
    new Library().myString.isEmpty();
  }
}