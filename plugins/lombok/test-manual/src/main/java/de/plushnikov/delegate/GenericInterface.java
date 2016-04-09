package de.plushnikov.delegate;

public interface GenericInterface {
  void test(int a);

  <T> T unwrap(Class<T> param);

}
