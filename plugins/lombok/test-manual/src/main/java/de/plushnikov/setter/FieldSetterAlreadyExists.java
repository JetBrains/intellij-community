package de.plushnikov.setter;

import lombok.Setter;

public class FieldSetterAlreadyExists {
  @Setter
  private int intProperty;

  void setIntProperty() {
  }

  public static void main(String[] args) {
    FieldSetterAlreadyExists bean = new FieldSetterAlreadyExists();
    bean.setIntProperty();
    bean.setIntProperty(123);
  }
}
