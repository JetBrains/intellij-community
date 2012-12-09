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

import com.intellij.designer.designSurface.*;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class RadLayout {
  protected RadComponent myContainer;

  public void setContainer(RadComponent container) {
    myContainer = container;
  }

  public void configureProperties(List<Property> properties) {
  }

  public void addComponentToContainer(RadComponent component, int index) {
  }

  public void removeComponentFromContainer(RadComponent component) {
  }

  public abstract ComponentDecorator getChildSelectionDecorator(RadComponent component, List<RadComponent> selection);

  @Nullable
  public EditOperation processChildOperation(OperationContext context) {
    return null;
  }

  public void addStaticDecorators(List<StaticDecorator> decorators, List<RadComponent> selection) {
  }

  public void addSelectionActions(DesignerEditorPanel designer,
                                  DefaultActionGroup actionGroup,
                                  JComponent shortcuts,
                                  List<RadComponent> selection) {
  }

  @Nullable
  public ICaption getCaption(RadComponent component) {
    return null;
  }

  public boolean isWrapIn(List<RadComponent> components) {
    return true;
  }
}