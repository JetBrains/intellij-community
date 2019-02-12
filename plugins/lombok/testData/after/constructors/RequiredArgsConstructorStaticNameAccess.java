import lombok.AccessLevel;

@lombok.RequiredArgsConstructor(staticName="of", access = AccessLevel.PRIVATE)
class RequiredArgsConstructorStaticNameAccessPrivate {
  final int x;
  String name;

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  private RequiredArgsConstructorStaticNameAccessPrivate(final int x) {
    this.x = x;
  }

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  private static RequiredArgsConstructorStaticNameAccessPrivate of(final int x) {
    return new RequiredArgsConstructorStaticNameAccessPrivate(x);
  }
}

@lombok.RequiredArgsConstructor(staticName="of", access = AccessLevel.PROTECTED)
class RequiredArgsConstructorStaticNameAccessProtected {
  final int x;
  String name;

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  private RequiredArgsConstructorStaticNameAccessProtected(final int x) {
    this.x = x;
  }

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  protected static RequiredArgsConstructorStaticNameAccessProtected of(final int x) {
    return new RequiredArgsConstructorStaticNameAccessProtected(x);
  }
}

@lombok.RequiredArgsConstructor(staticName="of", access = AccessLevel.PACKAGE)
class RequiredArgsConstructorStaticNameAccessPackage {
  final int x;
  String name;

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  private RequiredArgsConstructorStaticNameAccessPackage(final int x) {
    this.x = x;
  }

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  static RequiredArgsConstructorStaticNameAccessPackage of(final int x) {
    return new RequiredArgsConstructorStaticNameAccessPackage(x);
  }
}

@lombok.RequiredArgsConstructor(staticName="of", access = AccessLevel.MODULE)
class RequiredArgsConstructorStaticNameAccessModule {
  final int x;
  String name;

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  private RequiredArgsConstructorStaticNameAccessModule(final int x) {
    this.x = x;
  }

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  static RequiredArgsConstructorStaticNameAccessModule of(final int x) {
    return new RequiredArgsConstructorStaticNameAccessModule(x);
  }
}

@lombok.RequiredArgsConstructor(staticName="of", access = AccessLevel.PUBLIC)
class RequiredArgsConstructorStaticNameAccessPublic {
  final int x;
  String name;

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  private RequiredArgsConstructorStaticNameAccessPublic(final int x) {
    this.x = x;
  }

  @java.lang.SuppressWarnings("all")
  @javax.annotation.Generated("lombok")
  public static RequiredArgsConstructorStaticNameAccessPublic of(final int x) {
    return new RequiredArgsConstructorStaticNameAccessPublic(x);
  }
}

@lombok.RequiredArgsConstructor(staticName="of", access = AccessLevel.NONE)
class RequiredArgsConstructorStaticNameAccessNone {
  final int x;
  String name;
}
