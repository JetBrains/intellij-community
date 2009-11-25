class Test {
  ThreadLocal<Byte> i;

  void foo() {
    i.set(new Byte((byte) (i.get() + 1)));
    i.set(new Byte((byte) (i.get() + 1)));
    i.set(new Byte((byte) (i.get() - 1)));
    i.set(new Byte((byte) (i.get() - 1)));
    if (i.get() == 0);
  }
}