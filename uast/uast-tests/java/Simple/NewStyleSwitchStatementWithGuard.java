public class TypePattern {
  static String formatter(Object o) {
    String formatted;
    switch (o) {
      case Integer i when i < 0 ->
        formatted = String.format("int %d", i);
      case Integer i ->
        formatted = String.format("int %d", i);
      case Long l when l < 0 ->
        formatted = String.format("long %d", l);
      case Double d ->
        formatted = String.format("double %f", d);
      case String s ->
        formatted = String.format("String %s", s);
      default ->
        formatted = o.toString();
    }
    return formatted;
  }
}