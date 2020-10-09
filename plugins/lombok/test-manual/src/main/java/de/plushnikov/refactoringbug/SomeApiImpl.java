package de.plushnikov.refactoringbug;

public class SomeApiImpl implements SomeApi {
  @Override
  public int makeSomething(int param) {
    return param * param;
  }

  public static void main(String[] args) {
    SomeApiImpl someApi = new SomeApiImpl();
    someApi.makeSomething(20);
  }
}
