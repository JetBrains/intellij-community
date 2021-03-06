import lombok.val;

public class ValInspection {

  public void test() {
    val a = 1;

    val b = "a2";

    val c = new int[]{1};

    val d = System.getProperty("sss");

    // 'val' is not allowed in old-style for loops
    for (<error descr="'val' is not allowed in old-style for loops">val i = 0;</error> i < 10; i++) {
      val j = 2;
    }

    // 'val' is not compatible with array initializer expressions. Use the full form (new int[] { ... } instead of just { ... })
    <error descr="'val' is not compatible with array initializer expressions. Use the full form (new int[] { ... } instead of just { ... })">val e = <error descr="Array initializer is not allowed here">{"xyz"}</error>;</error>

    // 'val' is not allowed with lambda expressions.
    <error descr="'val' is not allowed with lambda expressions.">val f = <error descr="<lambda expression> is not a functional interface">() -> "xyz"</error>;</error>

    // 'val' on a local variable requires an initializer expression
    <error descr="'val' on a local variable requires an initializer expression">val g;</error>

    for (val h : <error descr="Cannot resolve symbol 'Arrays'">Arrays</error>.asList("a", "b", "c")) {
      System.out.println(h);
    }
  }

  public void test(<error descr="'val' works only on local variables and on foreach loops">val x</error>) {
    String val = "val";
    System.out.println(val);
  }
}
