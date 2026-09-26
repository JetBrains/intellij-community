// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class ComponentPtrDescriptor extends NodeDescriptor<ComponentPtr> {
  private ComponentPtr myPtr;
  /**
   * RadComponent.getBinding() or RadRootContainer.getClassToBind()
   */
  private String myBinding;
  private String myTitle;

  ComponentPtrDescriptor(final @NotNull NodeDescriptor parentDescriptor, final @NotNull ComponentPtr ptr) {
    super(null,parentDescriptor);

    myPtr=ptr;
  }

  @Override
  public boolean update() {
    myPtr.validate();
    if(!myPtr.isValid()) {
      myPtr=null;
      return true;
    }

    final String oldBinding = myBinding;
    final String oldTitle = myTitle;
    final RadComponent component = myPtr.getComponent();
    if (component.getModule().isDisposed()) {
      return false;
    }
    if(component instanceof RadRootContainer) {
      myBinding = ((RadRootContainer)component).getClassToBind();
    }
    else{
      myBinding = component.getBinding();
    }
    myTitle = component.getComponentTitle();
    return !Objects.equals(oldBinding, myBinding) || !Objects.equals(oldTitle, myTitle);
  }

  public @Nullable RadComponent getComponent() {
    return myPtr != null ? myPtr.getComponent() : null;
  }

  @Override
  public ComponentPtr getElement() {
    return myPtr;
  }
}
