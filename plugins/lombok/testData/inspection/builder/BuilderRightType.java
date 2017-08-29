<error descr="@lombok.Builder can be used on classes only">@lombok.Builder</error>
enum BuilderEnumError {
}

<error descr="@lombok.Builder can be used on classes only">@lombok.Builder</error>
interface BuilderInterfaceError {
}

<error descr="@lombok.Builder can be used on classes only">@lombok.Builder</error>
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

<error descr="Lombok annotations are not allowed on builder class.">@lombok.Builder</error>
class BuilderWithPredefinedClassAnnotation {
  private int x;
  private Float y;
  private String z;

  @lombok.ToString
  static class BuilderWithPredefinedClassAnnotationBuilder {
    private int x;
  }

}
