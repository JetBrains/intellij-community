// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

public class NoLibrary {
  private int bar;

  public int getBar() {
    return bar;
  }
  public class InstanceField {
    private int bar;
    private boolean Baz;
    private int fooBar;
    private int fieldWithoutGetter;

  public int getBar() {
      return bar;
    }

  public boolean isBaz() {
      return this.Baz;
    }

  public int getFooBar() {
      return InstanceField.this.fooBar;
    }
  }
  public class AllInstanceFields {
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

  public static int getBar() {
      return bar;
    }
  }
}