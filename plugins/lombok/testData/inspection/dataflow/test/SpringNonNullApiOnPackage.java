package test;

import lombok.Data;
import lombok.experimental.Accessors;

//IDEA-292093 @NonNullApi does not work on Lombok methods
public class SpringNonNullApiOnPackage {
  public void test() {
    // These trigger the "Passing 'null' argument to parameter annotated as @NotNull" inspection
    var a = new Explicit(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>).setNn(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    var b = <warning descr="Condition 'a == null' is always 'false'">a == null</warning>;

    // These do not
    var c = new Lombok(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>).setNn(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
    var d = <warning descr="Condition 'c == null' is always 'false'">c == null</warning>;
  }
}

class Explicit {
  public Explicit(String sexp) {
  }

  public String setNn(String s) {
    return s;
  }
}

@Data
@Accessors(chain = true)
class Lombok {
  private String nn;
  private final String slom;
}