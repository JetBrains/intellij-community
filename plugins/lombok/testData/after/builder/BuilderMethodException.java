class Exception {
}

public class BuilderMethodException {

  private static void foo(int i) throws Exception {
    System.out.println("sss");
  }

  public static void main(String[] args) {
    try {
      builder().i(2).build();
    } catch (Exception ignore) {
    }
  }

  public static VoidBuilder builder() {
    return new VoidBuilder();
  }

  public static class VoidBuilder {
    private int i;

    VoidBuilder() {
    }

    public VoidBuilder i(int i) {
      this.i = i;
      return this;
    }

    public void build() throws Exception {
      foo(i);
    }

    public String toString() {
      return "BuilderMethodException.VoidBuilder(i=" + this.i + ")";
    }
  }
}
