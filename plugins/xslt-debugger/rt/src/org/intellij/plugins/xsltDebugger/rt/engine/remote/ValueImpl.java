/*
 * Copyright 2007 Sascha Weinreuter
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

package org.intellij.plugins.xsltDebugger.rt.engine.remote;

import org.intellij.plugins.xsltDebugger.rt.engine.Value;

import java.io.Serializable;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 28.05.2007
 */
class ValueImpl implements Value {
  private final Serializable myValue;
  private final Type myType;

  public ValueImpl(Object value, Type type) {
    if (value instanceof Serializable) {
      myValue = (Serializable)value;
    } else {
      myValue = String.valueOf(value);
    }
    myType = type;
  }

  public Serializable getValue() {
    return myValue;
  }

  public Type getType() {
    return myType;
  }
}
