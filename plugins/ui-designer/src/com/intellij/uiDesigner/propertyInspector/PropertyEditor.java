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
package com.intellij.uiDesigner.propertyInspector;

import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.EventListenerList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class PropertyEditor<V> {
  private final EventListenerList myListenerList;

  protected PropertyEditor(){
    myListenerList=new EventListenerList();
  }

  /**
   * @return edited value. Note that {@code null} is the legal.
   *
   * @exception java.lang.Exception the method throws exception
   * if user enters wrong value and it cannot be applied. Note, that
   * exception's message will be shown to the user.
   */
  @Nullable
  public abstract V getValue() throws Exception;

  /**
   * @param component this component can be used to prepare editor UI
   * component
   *
   * @param value value to be edited. The editor should not
   * directly edit the passed object. Instead of this it must edit some
   * internal data and return the edited value by {@code getValue}
   * method.
   *
   * @param inplaceContext this is hint for the editor. This parameter is not {@code null}
   * in case if the editor is used for inplace editing. This hint is very useful.
   * For example string editor doesn't have a border when it is used
   * inside property inspector and has border when it is used for inspace editing.
   *
   * @return the component which is used to edit the property in UI.
   * The method must always return not {@code null} component.
   */
  public abstract JComponent getComponent(
    RadComponent component,
    V value,
    InplaceContext inplaceContext);

  /**
   * Property editor can return preferred focused component (if any) inside the component
   * which is returned by the {@link #getComponent(com.intellij.uiDesigner.radComponents.RadComponent,V,InplaceContext) } method.
   * This method is used as a hint to implement better focus handling.
   * {@code null} values means that editor relies on the UI editor in
   * determing preferred focused component.
   *
   * @param component cannot be null
   */
  public JComponent getPreferredFocusedComponent(@NotNull final JComponent component){
    return null;
  }

  /**
   * Editor should update UI of all its internal components to fit current
   * IDEA Look And Feel. We cannot directly update UI of the component
   * that is returned by {@link #getComponent(com.intellij.uiDesigner.radComponents.RadComponent,V,InplaceContext) } method
   * because hidden components that are not in the Swing tree can exist.
   */
  public abstract void updateUI();

  /**
   * Adds specified listener
   */
  public final void addPropertyEditorListener(final PropertyEditorListener l){
    myListenerList.add(PropertyEditorListener.class,l);
  }

  /**
   * Removes specified listener
   */
  public final void removePropertyEditorListener(final PropertyEditorListener l){
    myListenerList.remove(PropertyEditorListener.class,l);
  }

  protected final void fireEditingCancelled(){
    final PropertyEditorListener[] listeners=myListenerList.getListeners(PropertyEditorListener.class);
    for (PropertyEditorListener listener : listeners) {
      listener.editingCanceled(this);
    }
  }

  protected final void fireValueCommitted(final boolean continueEditing, final boolean closeEditorOnError) {
    final PropertyEditorListener[] listeners=myListenerList.getListeners(PropertyEditorListener.class);
    for (PropertyEditorListener listener : listeners) {
      listener.valueCommitted(this, continueEditing, closeEditorOnError);
    }
  }

  protected final void preferredSizeChanged(){
    final PropertyEditorListener[] listeners=myListenerList.getListeners(PropertyEditorListener.class);
    for (PropertyEditorListener listener : listeners) {
      listener.preferredSizeChanged(this);
    }
  }
}