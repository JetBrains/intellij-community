/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.toolbox;

import org.jetbrains.annotations.Nullable;

import java.util.NoSuchElementException;

/**
 * Encapsulates a value which may be either defined or not.
 * Useful when null must be a defined value.
 * Instances are immutable.
 * <br/>
 * User: dcheryasov
 */
public class Maybe<T> {
  private final boolean myIsDefined;
  private final T myValue;

  /**
   * @return true iff this instance has a defined value.
   */
  public boolean isDefined() {
    return myIsDefined;
  }


  /**
   * @return value if it is defined, or null.
   */
  @Nullable
  public T valueOrNull() {
    if (myIsDefined) return myValue;
    else return null;
  }

  /**
   * @return value if it is defined; else throws a NoSuchElementException.
   */
  public T value() {
    if (myIsDefined) return myValue;
    else throw new NoSuchElementException("Accessing undefined value of Maybe");
  }

  /**
   * Creates a defined instance.
   * @param value what to store.
   */
  public Maybe(T value) {
    myValue = value;
    myIsDefined = true;
  }

  /**
   * Creates an undefined instance.
   */
  public Maybe() {
    myValue = null;
    myIsDefined = false;
  }

  @Override
  public String toString() {
    if (myIsDefined) {
      if (myValue == null) return "?(null)";
      else return "?(" + myValue.toString() + ")";
    }
    else return "?_";
  }
}
