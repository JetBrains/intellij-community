import lombok.val;

public class LombokVal {

  public String someMethod(String <warning descr="Parameter 'param' can have 'final' modifier">param</warning>) {
    val someVar = "Constant";
    val someVar2 = someVar + param;

    return "Result: " + someVar2;
  }
}