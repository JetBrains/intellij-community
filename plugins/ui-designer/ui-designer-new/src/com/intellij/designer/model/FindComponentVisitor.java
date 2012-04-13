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
package com.intellij.designer.model;

import com.intellij.designer.designSurface.ComponentTargetFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public class FindComponentVisitor extends RadComponentVisitor {
  private final JComponent myComponent;
  private final ComponentTargetFilter myFilter;
  private RadComponent myResult;
  private final int myX;
  private final int myY;

  public FindComponentVisitor(@NotNull JComponent component, @Nullable ComponentTargetFilter filter, int x, int y) {
    myComponent = component;
    myFilter = filter;
    myX = x;
    myY = y;
  }

  public RadComponent getResult() {
    return myResult;
  }

  @Override
  public boolean visit(RadComponent component) {
    return myResult == null &&
           component.getBounds(myComponent).contains(myX, myY) &&
           (myFilter == null || myFilter.preFilter(component));
  }

  @Override
  public void endVisit(RadComponent component) {
    if (myResult == null && (myFilter == null || myFilter.resultFilter(component))) {
      myResult = component;
    }
  }
}