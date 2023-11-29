import lombok.experimental.Delegate;

public class FindUsageDelegateMethod {
  @Delegate
  private ClassB classB;
}

class ClassB {
  public void foo<caret>() {
  }
}

class ClassC {
  private FindUsageDelegateMethod classA;

  public void bar() {
    classA.foo();
  }
}