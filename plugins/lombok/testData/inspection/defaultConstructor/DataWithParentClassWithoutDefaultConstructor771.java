package inspection.defaultConstructor;

import lombok.Data;
import lombok.EqualsAndHashCode;

public class DataWithParentClassWithoutDefaultConstructor771 {
  public static void main(String[] args) {
    System.out.println(new Child());
  }
}

<error descr="Lombok needs a default constructor in the base class">@Data</error>
@EqualsAndHashCode(callSuper = true)
class Child extends Parent {
}

@Data
class Parent {
  public Parent(String msg) {
  }
}
