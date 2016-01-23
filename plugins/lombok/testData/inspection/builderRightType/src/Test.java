@lombok.Builder
enum BuilderEnumError {
}

@lombok.Builder
interface BuilderInterfaceError {
}

@lombok.Builder
@interface BuilderAnnotationError {
}

class BuilderOnStaticMethod {
  private int x;

  @lombok.Builder
  public static void makeMe(int x) {
    System.out.println(x);
  }
}

class BuilderOnConstructor {
  private int x;

  @lombok.Builder
  public BuilderOnConstructor(int x) {
    System.out.println(x);
  }
}

class BuilderOnNormalMethod {
  private int x;

  @lombok.Builder
  public void makeMe(int x) {
    System.out.println(x);
  }
}
import lombok.ToString;
@lombok.Builder
class BuilderWithPredefinedClassAnnotation {
  private int x;
  private Float y;
  private String z;

  @ToString
  static class BuilderWithPredefinedClassAnnotationBuilder {
    private int x;
  }

}