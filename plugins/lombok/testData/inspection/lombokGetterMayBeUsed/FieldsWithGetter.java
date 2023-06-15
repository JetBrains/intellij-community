// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

public class <warning descr="Class 'FieldsWithGetter' may use Lombok @Getter">FieldsWithGetter</warning> {
  private int bar;

  public int getBar() {
    return bar;
  }
  public class InstanceField {
    private int bar;
    private boolean Baz;
    private int fooBar;
    private int fieldWithoutGetter;

  <warning descr="Field 'bar' may have Lombok @Getter">public int getBar() {
      return bar;
    }</warning>

  <warning descr="Field 'Baz' may have Lombok @Getter">public boolean isBaz() {
      return this.Baz;
    }</warning>

  <warning descr="Field 'fooBar' may have Lombok @Getter">public int getFooBar() {
      return InstanceField.this.fooBar;
    }</warning>
  }
  public class <warning descr="Class 'AllInstanceFields' may use Lombok @Getter">AllInstanceFields</warning> {
    private int bar;
    private boolean Baz;
    private int fooBar;
    private static int staticFieldWithoutGetter;

    public int getBar() {
      return bar;
    }

    public boolean isBaz() {
      return this.Baz;
    }

    public int getFooBar() {
      return AllInstanceFields.this.fooBar;
    }
  }
  public class StaticField {
    private static int bar;
    private int fieldWithoutGetter;

  <warning descr="Field 'bar' may have Lombok @Getter">public static int getBar() {
      return bar;
    }</warning>
  }
}