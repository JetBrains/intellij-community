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
package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.Comparing;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import org.jetbrains.annotations.NotNull;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class ComponentPtrDescriptor extends NodeDescriptor<ComponentPtr> {
  private ComponentPtr myPtr;
  /**
   * RadComponent.getBinding() or RadRootContainer.getClassToBind()
   */
  private String myBinding;
  private String myTitle;

  public ComponentPtrDescriptor(@NotNull final NodeDescriptor parentDescriptor, @NotNull final ComponentPtr ptr) {
    super(null,parentDescriptor);

    myPtr=ptr;
  }

  public boolean update(){
    myPtr.validate();
    if(!myPtr.isValid()){
      myPtr=null;
      return true;
    }

    final String oldBinding = myBinding;
    final String oldTitle = myTitle;
    final RadComponent component = myPtr.getComponent();
    if(component instanceof RadRootContainer){
      myBinding = ((RadRootContainer)component).getClassToBind();
    }
    else{
      myBinding = component.getBinding();
    }
    myTitle = component.getComponentTitle();
    return !Comparing.equal(oldBinding,myBinding) || !Comparing.equal(oldTitle, myTitle);
  }

  public RadComponent getComponent() {
    return myPtr != null ? myPtr.getComponent() : null;
  }

  public ComponentPtr getElement() {
    return myPtr;
  }
}
