@lombok.experimental.UtilityClass
public <warning descr="@UtilityClass already marks the class final.">final</warning> class UtilityClassClass {
  public <warning descr="@UtilityClass already marks fields static.">static</warning> String testField;

  public <warning descr="@UtilityClass already marks methods static.">static</warning> void testMethod() {}
  public <warning descr="@UtilityClass already marks inner classes static.">static</warning> class TestInnerClass {}
}
