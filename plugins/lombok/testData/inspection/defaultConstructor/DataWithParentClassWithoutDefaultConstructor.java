@lombok.Data
class Parent {
  private final String a;
}

<error descr="Lombok needs a default constructor in the base class">@lombok.Data</error>
class Child extends Parent {
  private final String b;
}

public class DataWithParentClassWithoutDefaultConstructor {
  public static void main(String[] args) {
    System.out.println(new Child("xxx"));
  }
}
