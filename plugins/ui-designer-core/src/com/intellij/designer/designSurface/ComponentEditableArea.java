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

import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
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
public abstract class ComponentEditableArea implements EditableArea {
  private final JComponent myComponent;
  private final EventListenerList myListenerList = new EventListenerList();
  private List<RadComponent> mySelection = new ArrayList<>();

  public ComponentEditableArea(JComponent component) {
    myComponent = component;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Selection
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void addSelectionListener(ComponentSelectionListener listener) {
    myListenerList.add(ComponentSelectionListener.class, listener);
  }

  @Override
  public void removeSelectionListener(ComponentSelectionListener listener) {
    myListenerList.remove(ComponentSelectionListener.class, listener);
  }

  protected void fireSelectionChanged() {
    for (ComponentSelectionListener listener : myListenerList.getListeners(ComponentSelectionListener.class)) {
      listener.selectionChanged(this);
    }
  }

  @Override
  @NotNull
  public List<RadComponent> getSelection() {
    return mySelection;
  }

  @Override
  public boolean isSelected(@NotNull RadComponent component) {
    return mySelection.contains(component);
  }

  @Override
  public void select(@NotNull RadComponent component) {
    mySelection = new ArrayList<>();
    mySelection.add(component);
    fireSelectionChanged();
  }

  @Override
  public void deselect(@NotNull RadComponent component) {
    mySelection.remove(component);
    fireSelectionChanged();
  }

  @Override
  public void appendSelection(@NotNull RadComponent component) {
    mySelection.remove(component);
    mySelection.add(component);
    fireSelectionChanged();
  }

  @Override
  public void setSelection(@NotNull List<RadComponent> components) {
    mySelection = new ArrayList<>(components);
    fireSelectionChanged();
  }

  @Override
  public void deselect(@NotNull Collection<RadComponent> components) {
    mySelection.removeAll(components);
    fireSelectionChanged();
  }

  @Override
  public void deselectAll() {
    mySelection = new ArrayList<>();
    fireSelectionChanged();
  }

  @Override
  public void scrollToSelection() {
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Visual
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public void setCursor(@Nullable Cursor cursor) {
    myComponent.setCursor(cursor);
  }

  @Override
  public void setDescription(@Nullable String text) {
    ActionMenu.showDescriptionInStatusBar(true, myComponent, text);
  }

  @NotNull
  public JComponent getNativeComponent() {
    return myComponent;
  }

  public boolean isTree() {
    return false;
  }

  @Nullable
  public FeedbackTreeLayer getFeedbackTreeLayer() {
    return null;
  }
}
