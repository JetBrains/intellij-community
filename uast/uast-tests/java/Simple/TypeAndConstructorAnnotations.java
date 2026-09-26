// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package test.pkg;

@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE, java.lang.annotation.ElementType.CONSTRUCTOR})
public @interface A {}

@A
public class Foo {
  @A
  public Foo() {}
  public @A int foo1() {}
  public @A String foo2() {}
  public @A <T> T foo3() {}
}