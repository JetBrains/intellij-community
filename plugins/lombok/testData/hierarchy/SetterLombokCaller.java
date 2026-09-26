public class SetterLombokCaller {

  public void useSetter() {
    SetterLombok obj = new SetterLombok();
    obj.setValue("Hello");
    obj.setCount(42);
  }

  public void updateValue(SetterLombok lombok, String newValue) {
    lombok.setValue(newValue);
  }
}
