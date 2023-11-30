// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

public class <warning descr="Class 'InstanceAndStaticFields' may use Lombok @Setter">InstanceAndStaticFields</warning> {
  private static int staticField;
  private int instanceField;

  <warning descr="Field 'staticField' may have Lombok @Setter">public static void setStaticField(int param) {
    staticField = param;
  }</warning>

  public void setInstanceField(int param) {
    instanceField = param;
  }
}