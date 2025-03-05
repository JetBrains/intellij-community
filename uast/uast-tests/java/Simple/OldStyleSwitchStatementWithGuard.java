public class TypePattern {
  static String formatter(Object o) {
    String formatted;
    switch (o) {
      case Integer i when i < 0:
        formatted = String.format("int %d", i);
        break;
      case Integer i:
        formatted = String.format("int %d", i);
        break;
      case Long l when l < 0:
        formatted = String.format("long %d", l);
        break;
      case Double d:
        formatted = String.format("double %f", d);
        break;
      case String s:
        formatted = String.format("String %s", s);
        break;
      default:
        formatted = o.toString();
        break;
    }
    return formatted;
  }
}