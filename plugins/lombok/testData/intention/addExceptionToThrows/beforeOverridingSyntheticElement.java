import lombok.Getter;

public class A {
  @Getter
  private String s;

  private static class B extends A {
    @Override
    public String getS() {
      throw new RuntimeException();
    }
  }

  private static class C extends B {
    @Override
    public String getS() {
      throw new Runtime<caret>Exception();
    }
  }
}