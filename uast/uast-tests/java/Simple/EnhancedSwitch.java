public class Main {

  private static String getString() {
    var str = "baz";

    final String numericString =
      switch (str) {
        case "foo" -> "FOO";
        case "bar" -> "BAR";
        case "baz" -> "bAz";
        default -> "default";
      };

    final String numericString2 =
      switch (str) {
        case "foo":
          break "FOO";
        case "bar":
          break "BAR";
        case "baz":
          break "bAz";
        default:
          break "default";
      };
    return numericString + numericString2;
  }
}