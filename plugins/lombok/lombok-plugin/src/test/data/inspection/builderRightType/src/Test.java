@lombok.experimental.Builder
enum BuilderEnumError {
}

@lombok.experimental.Builder
interface BuilderInterfaceError {
}

@lombok.experimental.Builder
@interface BuilderAnnotationError {
}

class BuilderOnStaticMethod {
  private int x;

  @lombok.experimental.Builder
  public static void makeMe(int x) {
    System.out.println(x);
  }
}

class BuilderOnConstructor {
  private int x;

  @lombok.experimental.Builder
  public BuilderOnConstructor(int x) {
    System.out.println(x);
  }
}

class BuilderOnNormalMethod {
  private int x;

  @lombok.experimental.Builder
  public void makeMe(int x) {
    System.out.println(x);
  }
}