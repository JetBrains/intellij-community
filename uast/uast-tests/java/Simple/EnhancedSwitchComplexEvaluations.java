public class Main {

  private static String getOneOrTwoString(String unknown) {
    final String switchResult =
      switch (unknown) {
        case "foo", "bar" -> "foobar";
        default -> "default";
      };

    return switchResult;
  }

  private static String getOneOrThrow(String unknown) {
    final String switchResult =
      switch (unknown) {
        case "foo", "bar" -> "foobar";
        default -> throw new IllegalArgumentException();
      };

    return switchResult;
  }

  private static String getThrowOrOne(String unknown) {
    final String switchResult =
      switch (unknown) {
        case "foo", "bar" -> throw new IllegalArgumentException();
        default -> "foobar";
      };

    return switchResult;
  }

  private static String getUnknownString(String unknown) {
    final String switchResult =
      switch (unknown) {
        case "foo", "bar" -> "foobar" + unknown;
        default -> "default" + unknown;
      };

    return switchResult;
  }

  private static int getOneInsideOrThrow(String unknown) {
    String known = "abc";

    final int switchResult =
      switch (known) {
        case "foo":
        case "bar":
          break 0;
        default:
          if (unknown.length() > 0) {
            break 12;
          } else
            throw new IllegalArgumentException();
      };

    return switchResult;
  }


  private static int getThrowOrOneInside(String unknown) {
    String known = "abc";

    final int switchResult =
      switch (known) {
        case "foo":
        case "bar":
          break 0;
        default:
          if (unknown.length() <= 0) {
            throw new IllegalArgumentException();
          }
          else if (unknown.equals("true")) {
            break 12;
          }
          else {
            break 18;
          }
      };

    return switchResult;
  }

}