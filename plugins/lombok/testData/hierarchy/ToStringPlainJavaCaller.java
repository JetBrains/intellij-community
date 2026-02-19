public class ToStringPlainJavaCaller {

  public void useToString() {
    ToStringPlainJava obj = new ToStringPlainJava("Task description", 1, false);
    String result = obj.toString();
    System.out.println(result);
  }

  public String getStringRepresentation(ToStringPlainJava plainJava) {
    return plainJava.toString();
  }

  public void logObject(ToStringPlainJava plainJava) {
    System.out.println("Object: " + plainJava.toString());
  }
}
