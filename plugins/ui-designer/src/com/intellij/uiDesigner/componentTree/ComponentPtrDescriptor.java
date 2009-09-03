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
final class ComponentPtrDescriptor extends NodeDescriptor{
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

  public Object getElement(){
    return myPtr;
  }
}
