public class Main {

  private static String getString() {
    var str = "baz";

    final String numericString =
      switch (str) {
        case "foo" -> {
          System.out.println("here");
          yield "FOO";
        }
        case "bar", "beer" -> "BAR";
        case "baz" -> "bAz";
        default -> "default";
      };

    final String numericString2 =
      switch (str) {
        case "foo":
          yield "FOO";
        case "bar":
          yield "BAR";
        case "baz", "zub":
          System.out.println("here");
          yield "bAz";
        default:
          yield "default";
      };

    return numericString + numericString2;
  }

}