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
package com.intellij.designer.designSurface;

import com.intellij.designer.designSurface.tools.InputTool;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class EditableArea {
  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Selection
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private final EventListenerList myListenerList = new EventListenerList();
  private List<RadComponent> mySelection = new ArrayList<RadComponent>();

  public void addSelectionListener(ComponentSelectionListener listener) {
    myListenerList.add(ComponentSelectionListener.class, listener);
  }

  public void removeSelectionListener(ComponentSelectionListener listener) {
    myListenerList.remove(ComponentSelectionListener.class, listener);
  }

  protected void fireSelectionChanged() {
    for (ComponentSelectionListener listener : myListenerList.getListeners(ComponentSelectionListener.class)) {
      listener.selectionChanged(this);
    }
  }

  @NotNull
  public List<RadComponent> getSelection() {
    return mySelection;
  }

  public boolean isSelected(@NotNull RadComponent component) {
    return mySelection.contains(component);
  }

  public void select(@NotNull RadComponent component) {
    mySelection = new ArrayList<RadComponent>();
    mySelection.add(component);
    fireSelectionChanged();
  }

  public void deselect(@NotNull RadComponent component) {
    mySelection.remove(component);
    fireSelectionChanged();
  }

  public void appendSelection(@NotNull RadComponent component) {
    mySelection.remove(component);
    mySelection.add(component);
    fireSelectionChanged();
  }

  public void setSelection(@NotNull Collection<RadComponent> components) {
    mySelection = new ArrayList<RadComponent>(components);
    fireSelectionChanged();
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public abstract void setCursor(@Nullable Cursor cursor);

  @NotNull
  public abstract JComponent getNativeComponent();

  @Nullable
  public abstract RadComponent findTarget(int x, int y, @Nullable ComponentTargetFilter filter);

  @Nullable
  public abstract InputTool findTargetTool(int x, int y);

  public abstract ComponentDecorator getRootSelectionDecorator();

  @Nullable
  public EditOperation processRootOperation(OperationContext context) {
    return null;
  }

  public abstract FeedbackLayer getFeedbackLayer();

  public abstract RadComponent getRootComponent();

  public boolean isTree() {
    return false;
  }

  @Nullable
  public FeedbackTreeLayer getFeedbackTreeLayer() {
    return null;
  }
}