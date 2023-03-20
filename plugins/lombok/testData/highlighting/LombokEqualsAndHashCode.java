import lombok.Data;
import lombok.EqualsAndHashCode;

public class LombokEqualsAndHashCode {
  @Data
  static class Parent {
    private int i;
  }

  @Data
  @EqualsAndHashCode(callSuper = true, doNotUseGetters = true, of = {})
  static class Child1 extends Parent {
    private float f;
  }

  @Data
  @EqualsAndHashCode(callSuper = false, doNotUseGetters = <warning descr="Redundant default parameter value assignment">false</warning>)
  static class Child2 extends Parent {
    private float f;
  }

  public static void main(String[] args) {
    System.out.println(new Parent());
    System.out.println(new Child1());
    System.out.println(new Child2());
  }
}
