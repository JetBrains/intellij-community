// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

public class NoLibrary {
  private int bar;

  public void setBar(int param) {
    bar = param;
  }
  public class InstanceField {
    private int bar;
    private boolean PrimitiveBooleanValue;
    private int fooBar;
    private int fieldWithoutSetter;

    public void setBar(int param) {
      bar = param;
    }

    public void setPrimitiveBooleanValue(boolean param) {
      PrimitiveBooleanValue = param;
    }

  public void setFooBar(int param) {
      InstanceField.this.fooBar = param;
    }
  }
  public class AllInstanceFields {
    private int bar;
    private boolean Baz;
    private int fooBar;
    private static int staticFieldWithoutSetter;

    public void setBar(int param) {
      bar = param;
    }

    public void setBaz(boolean param) {
      Baz = param;
    }

    public void setFooBar(int param) {
      AllInstanceFields.this.fooBar = param;
    }
  }
  public class StaticField {
    private static int number;
    private int fieldWithoutSetter;

    public static void setNumber(int param) {
      number = param;
    }
  }
}