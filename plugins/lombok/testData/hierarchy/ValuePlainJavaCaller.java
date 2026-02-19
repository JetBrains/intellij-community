public class ValuePlainJavaCaller {

  public void useValue() {
    ValuePlainJava obj = new ValuePlainJava("ABC", 1, true);

    String code = obj.getCode();
    int version = obj.getVersion();
    boolean enabled = obj.isEnabled();

    System.out.println("Code: " + code + ", Version: " + version + ", Enabled: " + enabled);
  }

  public String fetchCode(ValuePlainJava value) {
    return value.getCode();
  }

  public boolean checkEnabled(ValuePlainJava value) {
    return value.isEnabled();
  }
}
