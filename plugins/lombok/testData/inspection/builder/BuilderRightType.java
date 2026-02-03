<error descr="@Builder is only supported on classes, records, constructors, and methods.">@lombok.Builder</error>
enum BuilderEnumError {
}

<error descr="@Builder is only supported on classes, records, constructors, and methods.">@lombok.Builder</error>
interface BuilderInterfaceError {
}

<error descr="@Builder is only supported on classes, records, constructors, and methods.">@lombok.Builder</error>
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

<error descr="Lombok's annotations are not allowed on builder class.">@lombok.Builder</error>
class BuilderWithPredefinedClassAnnotation {
  private int x;
  private Float y;
  private String z;

  @lombok.ToString
  static class BuilderWithPredefinedClassAnnotationBuilder {
    private int x;
  }

}

public class BuilderOnAnonymousClass {
  interface HelloWorld {
    void greet();
  }

  HelloWorld myWorld = new <error descr="'@lombok.Builder' not applicable to type use"><error descr="@Builder is only supported on classes, records, constructors, and methods.">@lombok.Builder</error></error> HelloWorld() {
    public void greet() {
      System.out.println("Hello World");
    }

  };

  public static void main(String[] args) {
  }
}