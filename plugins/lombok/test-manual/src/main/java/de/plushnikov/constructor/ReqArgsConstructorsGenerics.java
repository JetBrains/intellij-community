package de.plushnikov.constructor;


@lombok.RequiredArgsConstructor(staticName = "of")
class RequiredArgsConstructorStaticNameGenerics<T extends Number> {
  final T x;
  String name;
}

@lombok.RequiredArgsConstructor(staticName = "of")
class RequiredArgsConstructorStaticNameGenerics2<T extends Number> {
  final Class<T> x;
  String name;
}

public class ReqArgsConstructorsGenerics {

  public static void main(String[] args) {
    RequiredArgsConstructorStaticNameGenerics test = RequiredArgsConstructorStaticNameGenerics.of(23);
    RequiredArgsConstructorStaticNameGenerics2 test2 = RequiredArgsConstructorStaticNameGenerics2.of(Integer.class);
  }
}
