// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  public void setCursor(@Nullable Cursor cursor) {
    myComponent.setCursor(cursor);
  }

  @Override
  public void setDescription(@Nullable String text) {
    ActionMenu.showDescriptionInStatusBar(true, myComponent, text);
  }

  @Override
  @NotNull
  public JComponent getNativeComponent() {
    return myComponent;
  }

  @Override
  public boolean isTree() {
    return false;
  }

  @Override
  @Nullable
  public FeedbackTreeLayer getFeedbackTreeLayer() {
    return null;
  }
}
