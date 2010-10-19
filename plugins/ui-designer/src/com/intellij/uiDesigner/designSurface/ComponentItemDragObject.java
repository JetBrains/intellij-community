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

package com.intellij.uiDesigner.designSurface;

import com.intellij.uiDesigner.palette.ComponentItem;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.LoaderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author yole
 */
public class ComponentItemDragObject implements ComponentDragObject {
  private final ComponentItem myItem;

  public ComponentItemDragObject(@NotNull final ComponentItem item) {
    myItem = item;
  }

  public ComponentItem getItem() {
    return myItem;
  }

  public int getComponentCount() {
    return 1;
  }

  public boolean isHGrow() {
    return (myItem.getDefaultConstraints().getHSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0;
  }

  public boolean isVGrow() {
    return (myItem.getDefaultConstraints().getVSizePolicy() & GridConstraints.SIZEPOLICY_WANT_GROW) != 0;
  }

  public int getRelativeRow(int componentIndex) {
    return 0;
  }

  public int getRelativeCol(int componentIndex) {
    return 0;
  }

  public int getRowSpan(int componentIndex) {
    return 1;
  }

  public int getColSpan(int componentIndex) {
    return 1;
  }

  @Nullable
  public Point getDelta(int componentIndex) {
    return null;
  }

  @NotNull
  public Dimension getInitialSize(final RadContainer targetContainer) {
    final ClassLoader loader = LoaderFactory.getInstance(targetContainer.getProject()).getLoader(targetContainer.getModule());
    return myItem.getInitialSize(targetContainer.getDelegee(), loader);
  }
}
