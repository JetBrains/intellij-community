class Super {
  public void message<caret>() {
    System.out.println(this);
  }
}

class Sub extends Super {
  public void message() {
    super.message();      // <-- Inline this method call.
  }
}