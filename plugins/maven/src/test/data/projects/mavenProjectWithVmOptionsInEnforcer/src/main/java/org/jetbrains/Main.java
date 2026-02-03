// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains;

public class Main {
  public static void main(String[] args) {
    System.out.println("Hello from maven project with vm options in enforcer!");
    System.out.println("Maven JVM sees test.vmOption=" + System.getProperty("test.vmOption"));
    System.out.println("java.vm.name=" + System.getProperty("java.vm.name"));
    System.out.println("java.version=" + System.getProperty("java.version"));
  }
}