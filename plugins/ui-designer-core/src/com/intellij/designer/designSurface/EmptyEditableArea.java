/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.designer.designSurface;

import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public class EmptyEditableArea extends ComponentEditableArea {
  public EmptyEditableArea(JComponent component) {
    super(component);
  }

  @Nullable
  @Override
  public RadComponent findTarget(int x, int y, @Nullable ComponentTargetFilter filter) {
    return null;
  }

  @Nullable
  @Override
  public InputTool findTargetTool(int x, int y) {
    return null;
  }

  @Override
  public void showSelection(boolean value) {
  }

  @Override
  public ComponentDecorator getRootSelectionDecorator() {
    return null;
  }

  @Nullable
  @Override
  public EditOperation processRootOperation(OperationContext context) {
    return null;
  }

  @Override
  public FeedbackLayer getFeedbackLayer() {
    return null;
  }

  @Override
  public RadComponent getRootComponent() {
    return null;
  }

  @Override
  public ActionGroup getPopupActions() {
    return null;
  }

  @Override
  public String getPopupPlace() {
    return null;
  }
}