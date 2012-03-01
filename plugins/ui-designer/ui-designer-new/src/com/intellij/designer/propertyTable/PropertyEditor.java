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

import javax.swing.*;
import javax.swing.event.EventListenerList;

/**
 * @author Alexander Lobas
 */
public abstract class PropertyEditor {
  private final EventListenerList myListenerList = new EventListenerList();

  @NotNull
  public abstract JComponent getComponent(@Nullable RadComponent component, Object value);

  @Nullable
  public JComponent getPreferredFocusedComponent(@NotNull JComponent component) {
    return null;
  }

  public abstract Object getValue() throws Exception;

  public abstract void updateUI();

  public final void addPropertyEditorListener(PropertyEditorListener listener) {
    myListenerList.add(PropertyEditorListener.class, listener);
  }

  public final void removePropertyEditorListener(PropertyEditorListener listener) {
    myListenerList.remove(PropertyEditorListener.class, listener);
  }

  protected final void fireEditingCancelled() {
    for (PropertyEditorListener listener : myListenerList.getListeners(PropertyEditorListener.class)) {
      listener.editingCanceled(this);
    }
  }

  protected final void fireValueCommitted(boolean continueEditing, boolean closeEditorOnError) {
    for (PropertyEditorListener listener : myListenerList.getListeners(PropertyEditorListener.class)) {
      listener.valueCommitted(this, continueEditing, closeEditorOnError);
    }
  }

  protected final void preferredSizeChanged() {
    for (PropertyEditorListener listener : myListenerList.getListeners(PropertyEditorListener.class)) {
      listener.preferredSizeChanged(this);
    }
  }
}