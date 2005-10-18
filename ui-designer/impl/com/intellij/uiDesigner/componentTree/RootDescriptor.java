package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.NodeDescriptor;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class RootDescriptor extends NodeDescriptor{
  private final Object myRootElement;

  public RootDescriptor(final NodeDescriptor parentDescriptor,final Object rootElement){
    super(null,parentDescriptor);
    myRootElement=rootElement;
  }

  public boolean update(){
    return false;
  }

  public Object getElement(){
    return myRootElement;
  }
}
