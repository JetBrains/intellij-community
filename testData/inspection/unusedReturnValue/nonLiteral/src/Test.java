class Test {
  boolean foo() {
    return false;
  }

  boolean isUnused() {
    return !foo();
  }

  public static void main(String[] args) {
    new Test().isUnused();
  }
}
