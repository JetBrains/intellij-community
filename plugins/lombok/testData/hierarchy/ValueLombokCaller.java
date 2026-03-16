public class ValueLombokCaller {

  public void useValue() {
    ValueLombok obj = new ValueLombok("ABC", 1, true);

    String code = obj.getCode();
    int version = obj.getVersion();
    boolean enabled = obj.isEnabled();

    System.out.println("Code: " + code + ", Version: " + version + ", Enabled: " + enabled);
  }

  public String fetchCode(ValueLombok value) {
    return value.getCode();
  }

  public boolean checkEnabled(ValueLombok value) {
    return value.isEnabled();
  }
}
