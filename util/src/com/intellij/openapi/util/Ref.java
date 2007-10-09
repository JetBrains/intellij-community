/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
 * @author ven
 */
public class Ref<T> {
  private T myValue;

  public Ref() { }

  public Ref(T value) {
    myValue = value;
  }

  public boolean isNull () {
    return myValue == null;
  }

  public T get () {
    return myValue;
  }

  public void set (T value) {
    myValue = value;
  }

  public static <T> Ref<T> create(T value) {
    return new Ref<T>(value);
  }

  public String toString() {
    return myValue == null ? null : myValue.toString();
  }
}
