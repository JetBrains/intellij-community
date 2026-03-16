public class ToStringLombokCaller {

  public void useToString() {
    ToStringLombok obj = new ToStringLombok("Task description", 1, false);
    String result = obj.toString();
    System.out.println(result);
  }

  public String getStringRepresentation(ToStringLombok lombok) {
    return lombok.toString();
  }

  public void logObject(ToStringLombok lombok) {
    System.out.println("Object: " + lombok.toString());
  }
}
