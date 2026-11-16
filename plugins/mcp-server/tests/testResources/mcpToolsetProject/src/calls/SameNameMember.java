package calls;

class SameNameMember {
  int value;

  int value() {
    return value;
  }

  void caller() {
    value();
  }
}
