/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.uiDesigner.propertyTable;

import com.intellij.uiDesigner.model.RadComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class Property {
  private final Property myParent;
  @NotNull private final String myName;

  public Property(Property parent, @NotNull @NonNls String name) {

    myParent = parent;
    myName = name;
  }

  public final Property getParent() {
    return myParent;
  }

  @NotNull
  public final String getName() {
    return myName;
  }

  public List<Property> getChildren(RadComponent component) {
    return null;
  }

  public Object getValue(RadComponent component) {
    return null;
  }

  @NotNull
  public abstract PropertyRenderer getRenderer();

  @Nullable
  public abstract PropertyEditor getEditor();
}