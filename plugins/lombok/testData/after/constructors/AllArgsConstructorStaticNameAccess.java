import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import lombok.AccessLevel;

@AllArgsConstructor(staticName="of", access = AccessLevel.PRIVATE)
class AllArgsConstructorStaticNameAccessPrivate {
  final int x;
  String name;

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  private AllArgsConstructorStaticNameAccessPrivate(final int x, final String name) {
    this.x = x;
    this.name = name;
  }

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  private static AllArgsConstructorStaticNameAccessPrivate of(final int x, final String name) {
    return new AllArgsConstructorStaticNameAccessPrivate(x, name);
  }
}

@AllArgsConstructor(staticName="of", access = AccessLevel.PROTECTED)
class AllArgsConstructorStaticNameAccessProtected {
  final int x;
  String name;

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  private AllArgsConstructorStaticNameAccessProtected(final int x, final String name) {
    this.x = x;
    this.name = name;
  }

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  protected static AllArgsConstructorStaticNameAccessProtected of(final int x, final String name) {
    return new AllArgsConstructorStaticNameAccessProtected(x, name);
  }
}

@AllArgsConstructor(staticName="of", access = AccessLevel.PACKAGE)
class AllArgsConstructorStaticNameAccessPackage {
  final int x;
  String name;

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  private AllArgsConstructorStaticNameAccessPackage(final int x, final String name) {
    this.x = x;
    this.name = name;
  }

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  static AllArgsConstructorStaticNameAccessPackage of(final int x, final String name) {
    return new AllArgsConstructorStaticNameAccessPackage(x, name);
  }
}

@AllArgsConstructor(staticName="of", access = AccessLevel.MODULE)
class AllArgsConstructorStaticNameAccessModule {
  final int x;
  String name;

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  private AllArgsConstructorStaticNameAccessModule(final int x, final String name) {
    this.x = x;
    this.name = name;
  }

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  static AllArgsConstructorStaticNameAccessModule of(final int x, final String name) {
    return new AllArgsConstructorStaticNameAccessModule(x, name);
  }
}

@AllArgsConstructor(staticName="of", access = AccessLevel.PUBLIC)
class AllArgsConstructorStaticNameAccessPublic {
  final int x;
  String name;

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  private AllArgsConstructorStaticNameAccessPublic(final int x, final String name) {
    this.x = x;
    this.name = name;
  }

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  public static AllArgsConstructorStaticNameAccessPublic of(final int x, final String name) {
    return new AllArgsConstructorStaticNameAccessPublic(x, name);
  }
}

@AllArgsConstructor(staticName="of", access = AccessLevel.NONE)
class AllArgsConstructorStaticNameAccessNone {
  final int x;
  String name;
}
