// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.componentTree;

import com.intellij.ide.util.treeView.NodeDescriptor;

final class RootDescriptor extends NodeDescriptor{
  private final Object myRootElement;

  RootDescriptor(final NodeDescriptor parentDescriptor,final Object rootElement){
    super(null,parentDescriptor);
    myRootElement=rootElement;
  }

  @Override
  public boolean update(){
    return false;
  }

  @Override
  public Object getElement(){
    return myRootElement;
  }
}
