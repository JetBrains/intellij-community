// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

public class <warning descr="Class 'InstanceAndStaticFields' may use Lombok @Getter">InstanceAndStaticFields</warning> {
  private static int staticField;
  private int instanceField;

  <warning descr="Field 'staticField' may have Lombok @Getter">public static int getStaticField() {
    return staticField;
  }</warning>

  public int getInstanceField() {
    return instanceField;
  }
}