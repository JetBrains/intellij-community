import lombok.ToString;

@ToString
class ToStringOuter {
  final int x;
  String name;

  @ToString
  class ToStringInner {
    final int y;
  }

  @ToString
  static class ToStringStaticInner {
    final int y;
  }

  class ToStringMiddle {
    @ToString
    class ToStringMoreInner {
      final String name;
    }
  }
}