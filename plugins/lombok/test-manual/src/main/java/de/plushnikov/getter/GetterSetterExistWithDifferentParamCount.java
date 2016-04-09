package de.plushnikov.getter;

import lombok.Getter;
import lombok.Setter;

public class GetterSetterExistWithDifferentParamCount {
  @Getter
  @Setter
  private int intProperty;

  @Getter
  @Setter
  private String stringProperty;

  public void setIntProperty() {

  }

  public void setIntProperty(int a, float b) {

  }

  public void setIntProperty(int a, int b) {

  }

  public int getIntProperty(int a) {
    return intProperty;
  }

  public int getIntProperty(int a, int b) {
    return intProperty;
  }

  public int getIntProperty(int a, float b) {
    return intProperty;
  }

  public static void main(String[] args) {
    GetterSetterExistWithDifferentParamCount test = new GetterSetterExistWithDifferentParamCount();

    test.getIntProperty();
    test.setIntProperty(4);

    test.setIntProperty();
    test.setIntProperty(1, 2.0f);
    test.setIntProperty(1, 2);

    test.getIntProperty();
    test.getIntProperty(1, 2);
    test.getIntProperty(1, 2.0f);


    test.getStringProperty();
    test.setStringProperty("aaa");
  }
}
