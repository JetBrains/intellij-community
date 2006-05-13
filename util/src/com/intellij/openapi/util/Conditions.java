/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.util;

/**
 * @author max
 */
public class Conditions {
  private Conditions() {}

  public static <T> Condition<T> alwaysTrue() {
    return TRUE;
  }
  public static <T> Condition<T> alwaysFalse() {
    return FALSE;
  }

  public static <T> Condition<T> not(Condition<T> c) {
    return new Not<T>(c);
  }
  public static <T> Condition<T> and(Condition<T> c1, Condition<T> c2) {
    return new And<T>(c1, c2);
  }
  public static <T> Condition<T> or(Condition<T> c1, Condition<T> c2) {
    return new Or<T>(c1, c2);
  }

  private static class Not<T> implements Condition<T> {
    private final Condition<T> myCondition;

    public Not(Condition<T> condition) {
      myCondition = condition;
    }

    public boolean value(T value) {
      return !myCondition.value(value);
    }
  }
  private static class And<T> implements Condition<T>  {
    private Condition<T> t1;
    private Condition<T> t2;

    public And(final Condition<T> t1, final Condition<T> t2) {
      this.t1 = t1;
      this.t2 = t2;
    }

    public boolean value(final T object) {
      return t1.value(object) && t2.value(object);
    }
  }
  private static class Or<T> implements Condition<T>  {
    private Condition<T> t1;
    private Condition<T> t2;

    public Or(final Condition<T> t1, final Condition<T> t2) {
      this.t1 = t1;
      this.t2 = t2;
    }

    public boolean value(final T object) {
      return t1.value(object) || t2.value(object);
    }
  }

  public static Condition TRUE = new Condition() {
    public boolean value(final Object object) {
      return true;
    }
  };
  public static Condition FALSE = new Condition() {
    public boolean value(final Object object) {
      return false;
    }
  };
}
