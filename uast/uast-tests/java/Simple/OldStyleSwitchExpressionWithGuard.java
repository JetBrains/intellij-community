public class TypePattern {
  static String formatter(Object o) {
    String formatted = switch (o) {
      case Integer i when i < 0:
        yield String.format("int %d", i);
      case Integer i:
        yield String.format("int %d", i);
      case Long l when l < 0:
        yield String.format("long %d", l);
      case Double d:
        yield String.format("double %f", d);
      case String s:
        yield String.format("String %s", s);
      default:
        yield formatted = o.toString();
    };
    return formatted;
  }
}