public class SetterPlainJavaCaller {

  public void useSetter() {
    SetterPlainJava obj = new SetterPlainJava();
    obj.setValue("Hello");
    obj.setCount(42);
  }

  public void updateValue(SetterPlainJava plainJava, String newValue) {
    plainJava.setValue(newValue);
  }
}
