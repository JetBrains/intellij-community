class SneakyThrowsSingle {
  @lombok.SneakyThrows(Throwable.class)
  public void test() {
    System.out.println("test1");
  }

  @lombok.SneakyThrows(java.io.IOException.class)
  public void test2() {
    System.out.println("test2");
    throw new java.io.IOException();
  }

  @lombok.SneakyThrows(value=java.io.IOException.class)
  public void test3() {
    System.out.println("test3");
    throw new NoSuchMethodException();
  }

}