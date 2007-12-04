package com.intellij.openapi.vcs.update;

public class CommonStatusProjectAction extends AbstractCommonUpdateAction {
  public CommonStatusProjectAction() {
    super(ActionInfo.STATUS, ScopeInfo.PROJECT);
  }

  protected boolean filterRootsBeforeAction() {
    return false;
  }

}
