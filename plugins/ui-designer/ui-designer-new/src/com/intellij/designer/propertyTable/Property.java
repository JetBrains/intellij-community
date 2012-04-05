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
package com.intellij.designer.propertyTable;

import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class Property<T extends RadComponent> {
  private final Property myParent;
  private final String myName;
  private boolean myImportant;
  private boolean myExpert;
  private boolean myDeprecated;

  public Property(@Nullable Property parent, @NotNull String name) {
    myParent = parent;
    myName = name;
  }

  public Property<T> createForNewPresentation() {
    return createForNewPresentation(myParent, myName);
  }

  public abstract Property<T> createForNewPresentation(@Nullable Property parent, @NotNull String name);
  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Hierarchy
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public final Property getParent() {
    return myParent;
  }

  public List<Property<T>> getChildren(@Nullable T component) {
    return Collections.emptyList();
  }

  public int getIndent() {
    if (myParent != null) {
      return myParent.getParent() != null ? 2 : 1;
    }
    return 0;
  }

  public String getPath() {
    return myParent == null ? myName : myParent.getPath() + "/" + myName;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Value
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public Object getValue(T component) throws Exception {
    return null;
  }

  public void setValue(T component, Object value) throws Exception {
  }

  public boolean isDefaultValue(T component) throws Exception {
    return false;
  }

  public void setDefaultValue(T component) throws Exception {
  }

  public boolean availableFor(List<RadComponent> components) {
    return true;
  }

  public boolean needRefreshPropertyList() {
    return false;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Presentation
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @NotNull
  public final String getName() {
    return myName;
  }

  public boolean isImportant() {
    return myImportant;
  }

  public void setImportant(boolean important) {
    myImportant = important;
  }

  public boolean isExpert() {
    return myExpert;
  }

  public void setExpert(boolean expert) {
    myExpert = expert;
  }

  public boolean isDeprecated() {
    return myDeprecated;
  }

  public void setDeprecated(boolean deprecated) {
    myDeprecated = deprecated;
  }

  @NotNull
  public abstract PropertyRenderer getRenderer();

  @Nullable
  public abstract PropertyEditor getEditor();
}