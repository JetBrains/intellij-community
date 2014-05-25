class SneakyThrowsMultiple {
  public AutoCloseable foo() throws java.io.IOException {
    return null;
  }

  @lombok.SneakyThrows
  public void bar () {
    try (AutoCloseable foo = foo()) {
      // coded
      int a;
    }
  }
}