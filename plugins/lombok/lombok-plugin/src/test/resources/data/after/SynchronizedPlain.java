class SynchronizedPlain1 {


  void test() {
    synchronized (this.$lock) {
      System.out.println("one");
    }
  }

  void test2() {
    synchronized (this.$lock) {
      System.out.println("two");
    }
  }

  @java.lang.SuppressWarnings("all")
  private final java.lang.Object $lock = new java.lang.Object[0];
}

class SynchronizedPlain2 {


  static void test() {
    synchronized (SynchronizedPlain2.$LOCK) {
      System.out.println("three");
    }
  }

  static void test2() {
    synchronized (SynchronizedPlain2.$LOCK) {
      System.out.println("four");
    }
  }

  @java.lang.SuppressWarnings("all")
  private static final java.lang.Object $LOCK = new java.lang.Object[0];
}