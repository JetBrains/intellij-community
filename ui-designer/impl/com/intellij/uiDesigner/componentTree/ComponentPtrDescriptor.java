package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.Comparing;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadRootContainer;

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

  public ComponentPtrDescriptor(final NodeDescriptor parentDescriptor,final ComponentPtr ptr){
    super(null,parentDescriptor);
    if(parentDescriptor==null){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("parentDescriptor cannot be null");
    }
    if(ptr==null){
      //noinspection HardCodedStringLiteral
      throw new IllegalArgumentException("ptr cannot be null");
    }

    myPtr=ptr;
  }

  public boolean update(){
    myPtr.validate();
    if(!myPtr.isValid()){
      myPtr=null;
      return true;
    }

    final String oldBinding = myBinding;
    final RadComponent component = myPtr.getComponent();
    if(component instanceof RadRootContainer){
      myBinding = ((RadRootContainer)component).getClassToBind();
    }
    else{
      myBinding = component.getBinding();
    }
    return !Comparing.equal(oldBinding,myBinding);
  }

  public RadComponent getComponent() {
    return myPtr != null ? myPtr.getComponent() : null; 
  }
  
  public Object getElement(){
    return myPtr;
  }
}
