class RequiredArgsConstructor1 {
  final int x;
  String name;

  @java.beans.ConstructorProperties({"x"})
  @java.lang.SuppressWarnings("all")
  public RequiredArgsConstructor1(final int x) {
    this.x = x;
  }
}

class RequiredArgsConstructorAccess {
  final int x;
  String name;

  @java.beans.ConstructorProperties({"x"})
  @java.lang.SuppressWarnings("all")
  protected RequiredArgsConstructorAccess(final int x) {
    this.x = x;
  }
}

class RequiredArgsConstructorStaticName {
  final int x;
  String name;

  @java.lang.SuppressWarnings("all")
  private RequiredArgsConstructorStaticName(final int x) {
    this.x = x;
  }

  @java.lang.SuppressWarnings("all")
  public static RequiredArgsConstructorStaticName staticname(final int x) {
    return new RequiredArgsConstructorStaticName(x);
  }
}

class AllArgsConstructor1 {
  final int x;
  String name;

  @java.beans.ConstructorProperties({"x", "name"})
  @java.lang.SuppressWarnings("all")
  public AllArgsConstructor1(final int x, final String name) {
    this.x = x;
    this.name = name;
  }
}

class NoArgsConstructor1 {
  final int x;
  String name;

  @java.lang.SuppressWarnings("all")
  public NoArgsConstructor1() {
  }
}