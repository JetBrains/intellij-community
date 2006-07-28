package com.intellij.openapi.roots.impl;



/**
 *  @author dsl
 */
public abstract class RootModelComponentBase {
  RootModelImpl myRootModel;
  private boolean myDisposed;

  RootModelComponentBase(RootModelImpl rootModel) {
    rootModel.myComponents.add(this);
    myRootModel = rootModel;
  }

  protected void projectOpened() {

  }

  protected void projectClosed() {

  }

  protected void moduleAdded() {

  }

  RootModelImpl getRootModel() {
    return myRootModel;
  }

  protected void dispose() {
    myRootModel.myComponents.remove(this);
    myDisposed = true;
  }

  protected boolean isDisposed() {
    return myDisposed;
  }
}
