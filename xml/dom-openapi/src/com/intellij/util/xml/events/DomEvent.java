/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.xml.events;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DomEvent {
  public static final DomEvent[] EMPTY_ARRAY = new DomEvent[0];

  private final DomElement myElement;
  private final boolean myDefined;

  public DomEvent(@NotNull final DomElement element, boolean defined) {
    myDefined = defined;
    myElement = DomUtil.getFileElement(element);
  }

  public final DomElement getElement() {
    return myElement;
  }

  public boolean isDefined() {
    return myDefined;
  }

  public String toString() {
    return (myDefined ? "Defined " : "Changed ") + myElement;
  }

}
