// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public @Nullable EditOperation processChildOperation(OperationContext context) {
    return null;
  }

  public void addStaticDecorators(List<StaticDecorator> decorators, List<RadComponent> selection) {
  }

  public void addSelectionActions(DesignerEditorPanel designer,
                                  DefaultActionGroup actionGroup,
                                  JComponent shortcuts,
                                  List<RadComponent> selection) {
  }

  public @Nullable ICaption getCaption(RadComponent component) {
    return null;
  }

  public boolean isWrapIn(List<RadComponent> components) {
    return true;
  }
}