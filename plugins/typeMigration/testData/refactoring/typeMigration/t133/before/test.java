interface I {
  void f();
}

interface J extends I {
  void g();
}

public interface Test {
  void h(J i);
}

class B implements Test {
  @Override
  public void h(J i) {
    i.f();
  }
} 

class C extends B {
  @Override
  public void h(J i) {
    i.f();
  }
}

abstract class F implements Test {}

class FF extends F {
  @Override
  public void h(J i) {
  }
}
