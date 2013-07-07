class ToStringOuter {

  final int x;
  String name;

  class ToStringInner {

    final int y;

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
      return "ToStringOuter.ToStringInner(y=" + this.y + ")";
    }
  }

  static class ToStringStaticInner {

    final int y;

    @java.lang.Override
    @java.lang.SuppressWarnings("all")
    public java.lang.String toString() {
      return "ToStringOuter.ToStringStaticInner(y=" + this.y + ")";
    }
  }

  class ToStringMiddle {


    class ToStringMoreInner {

      final String name;

      @java.lang.Override
      @java.lang.SuppressWarnings("all")
      public java.lang.String toString() {
        return "ToStringOuter.ToStringMiddle.ToStringMoreInner(name=" + this.name + ")";
      }
    }
  }

  @java.lang.Override
  @java.lang.SuppressWarnings("all")
  public java.lang.String toString() {
    return "ToStringOuter(x=" + this.x + ", name=" + this.name + ")";
  }
}