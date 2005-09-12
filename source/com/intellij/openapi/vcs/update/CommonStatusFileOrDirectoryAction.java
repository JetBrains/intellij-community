package com.intellij.openapi.vcs.update;

public class CommonStatusFileOrDirectoryAction extends AbstractCommonUpdateAction{
  public CommonStatusFileOrDirectoryAction() {
    super(ActionInfo.STATUS, ScopeInfo.FILES);
  }

  protected boolean filterRootsBeforeAction() {
    return true;
  }
}
