// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

public class <warning descr="Class 'FieldsWithSetter' may use Lombok @Setter">FieldsWithSetter</warning> {
  private int bar;

  public void setBar(int param) {
    bar = param;
  }
  public class InstanceField {
    private int bar;
    private boolean Baz;
    private int fooBar;
    private int fieldWithoutSetter;

  <warning descr="Field 'bar' may have Lombok @Setter">public void setBar(int param) {
      bar = param;
    }</warning>

  <warning descr="Field 'Baz' may have Lombok @Setter">public void setBaz(boolean param) {
      this.Baz = param;
    }</warning>

  <warning descr="Field 'fooBar' may have Lombok @Setter">public void setFooBar(int param) {
      InstanceField.this.fooBar = param;
    }</warning>
  }
  public class <warning descr="Class 'AllInstanceFields' may use Lombok @Setter">AllInstanceFields</warning> {
    private int bar;
    private boolean Baz;
    private int fooBar;
    private static int staticFieldWithoutSetter;

    public void setBar(int param) {
      bar = param;
    }

    public void setBaz(boolean param) {
      this.Baz = param;
    }

    public void setFooBar(int param) {
      AllInstanceFields.this.fooBar = param;
    }
  }
  public class StaticField {
    private static int bar;
    private int fieldWithoutSetter;

  <warning descr="Field 'bar' may have Lombok @Setter">public static void setBar(int param) {
      bar = param;
    }</warning>
  }
}