class Exception {
}

public class BuilderMethodException {
  @lombok.Builder
  private static void foo(int i) throws Exception {
    System.out.println("sss");
  }

  public static void main(String[] args) {
    try {
      builder().i(2).build();
    } catch (Exception ignore) {
    }
  }
}

