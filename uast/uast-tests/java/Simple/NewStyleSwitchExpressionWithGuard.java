public class TypePattern {
  static String formatter(Object o) {
    String formatted = switch (o) {
      case Integer i when i < 0 -> String.format("int %d", i);
      case Integer i -> String.format("int %d", i);
      case Long l when l < 0 -> String.format("long %d", l);
      case Double d -> String.format("double %f", d);
      case String s -> String.format("String %s", s);
      default -> formatted = o.toString();
    };
    return formatted;
  }
}