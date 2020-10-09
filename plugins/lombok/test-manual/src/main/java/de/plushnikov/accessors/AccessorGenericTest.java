package de.plushnikov.accessors;

import lombok.Data;
import lombok.experimental.Accessors;

@Accessors(fluent = true, chain = true, prefix = {"my", ""})
@Data
public class AccessorGenericTest<T> {

  private T genericValue;
  private T mySomeValue;
  private float anotherValue;

  public AccessorGenericTest<T> zumVergleich(T someParam) {
    genericValue = someParam;
    return this;
  }

  public AccessorGenericTest(T mySomeValue) {
    this.genericValue = mySomeValue;
    this.mySomeValue = mySomeValue;
  }

  public static void main(String[] args) {
    AccessorGenericTest<Integer> genericTest = new AccessorGenericTest<Integer>(1);
    int inttt = genericTest.genericValue();
    System.out.println(genericTest.genericValue(234).genericValue().hashCode());

    int a = genericTest.someValue();
    System.out.println(a);
    float b = genericTest.anotherValue();
    System.out.println(b);

    genericTest.anotherValue(11.11f);
    genericTest.someValue(11);
    System.out.println(genericTest.someValue());
    System.out.println(genericTest.anotherValue());
  }
}
