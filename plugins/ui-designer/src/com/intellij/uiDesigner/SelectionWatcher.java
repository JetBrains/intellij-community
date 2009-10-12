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
package com.intellij.uiDesigner;

import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public abstract class SelectionWatcher {
  private final MyPropertyChangeListener myChangeListener;
  private RadRootContainer myRootContainer;
  private final GuiEditor myEditor;
  private final HierarchyChangeListener myHierarchyChangeListener;

  public SelectionWatcher(final GuiEditor editor) {
    myEditor = editor;
    myChangeListener = new MyPropertyChangeListener();
    myRootContainer = editor.getRootContainer();
    install(myRootContainer);

    myHierarchyChangeListener = new HierarchyChangeListener() {
      public void hierarchyChanged() {
        if (myRootContainer != editor.getRootContainer()) {
          deinstall(myRootContainer);
          myRootContainer = editor.getRootContainer();
          install(myRootContainer);
        }
      }
    };
    editor.addHierarchyChangeListener(myHierarchyChangeListener);
  }

  public void dispose() {
    deinstall(myRootContainer);
    myEditor.removeHierarchyChangeListener(myHierarchyChangeListener);
  }

  private void install(@NotNull final RadComponent component){
    component.addPropertyChangeListener(myChangeListener);
    if(component instanceof RadContainer){
      final RadContainer container = (RadContainer)component;
      for(int i = container.getComponentCount() - 1; i>= 0; i--){
        install(container.getComponent(i));
      }
    }
  }

  private void deinstall(@NotNull final RadComponent component){
    component.removePropertyChangeListener(myChangeListener);
    if(component instanceof RadContainer){
      final RadContainer container = (RadContainer)component;
      for(int i = container.getComponentCount() - 1; i>= 0; i--){
        deinstall(container.getComponent(i));
      }
    }
  }

  protected abstract void selectionChanged(RadComponent component, boolean selected);

  private final class MyPropertyChangeListener implements PropertyChangeListener{
    public void propertyChange(final PropertyChangeEvent e) {
      if(RadComponent.PROP_SELECTED.equals(e.getPropertyName())){
        final Boolean selected = (Boolean)e.getNewValue();
        selectionChanged((RadComponent)e.getSource(), selected.booleanValue());
      }
      else if(RadContainer.PROP_CHILDREN.equals(e.getPropertyName())){
        final RadComponent[] oldChildren = (RadComponent[])e.getOldValue();
        for(int i = oldChildren.length - 1; i >= 0; i--){
          deinstall(oldChildren[i]);
        }

        final RadComponent[] newChildren = (RadComponent[])e.getNewValue();
        for(int i = newChildren.length - 1; i >= 0; i--){
          install(newChildren[i]);
        }
      }
    }
  }
}
