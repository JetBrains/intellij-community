<error descr="@SuperBuilder is only supported on classes.">@lombok.experimental.SuperBuilder</error>
enum BuilderEnumError {
}

<error descr="@SuperBuilder is only supported on classes.">@lombok.experimental.SuperBuilder</error>
interface BuilderInterfaceError {
}

<error descr="@SuperBuilder is only supported on classes.">@lombok.experimental.SuperBuilder</error>
@interface BuilderAnnotationError {
}

public class SuperBuilderOnAnonymousClass {
  interface HelloWorld {
    void greet();
  }

  HelloWorld myWorld = new <error descr="'@lombok.experimental.SuperBuilder' not applicable to type use"><error descr="@SuperBuilder is only supported on classes.">@lombok.experimental.SuperBuilder</error></error> HelloWorld() {
    public void greet() {
      System.out.println("Hello World");
    }
  };

  public static void main(String[] args) {
  }
}
