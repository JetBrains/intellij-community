// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.componentTree;

import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import org.jetbrains.annotations.NotNull;

public final class ComponentPtr{
  private final GuiEditor myEditor;
  private final String myId;
  private RadComponent myComponent;

  /**
   */
  public ComponentPtr(final @NotNull GuiEditor editor, final @NotNull RadComponent component) {
    this(editor, component, true);
  }

  /**
   */
  public ComponentPtr(final @NotNull GuiEditor editor, final @NotNull RadComponent component, final boolean validate){
    myEditor=editor;
    myId=component.getId();

    if (validate) {
      validate();
      if(!isValid()){
      throw new IllegalArgumentException("invalid component: "+component);
      }
    }
    else {
      myComponent = component;
    }
  }

  /**
   * @return {@code RadComponent} which was calculated by the last
   * {@code validate} method.
   */
  public RadComponent getComponent(){
    return myComponent;
  }

  /**
   * @return {@code true} if and only if the pointer is valid.
   * It means that last {@code validate} call was successful and
   * pointer refers to live component.
   */
  public boolean isValid(){
    return myComponent!=null;
  }

  /**
   * Validates (updates) the state of the pointer
   */
  public void validate(){
    // Try to find component with myId starting from root container
    final RadContainer container=myEditor.getRootContainer();
    myComponent= (RadComponent)FormEditingUtil.findComponent(container,myId);
  }

  public boolean equals(final Object obj){
    if(!(obj instanceof ComponentPtr)){
      return false;
    }
    return myId.equals(((ComponentPtr)obj).myId);
  }

  public int hashCode(){
    return myId.hashCode();
  }
}
