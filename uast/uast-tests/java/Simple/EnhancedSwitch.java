public class Main {

  private static String getString() {
    var str = "abc";

    final String numericString =
      switch (str) {
        case "foo" -> {
          System.out.println("here");
          break "FOO";
        }
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
          System.out.println("here");
          break "bAz";
        default:
          break "default";
      };
    return numericString + numericString2;
  }
}