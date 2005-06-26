package com.intellij.openapi.vcs;

public class VcsShowOptionsSettingImpl extends VcsAbstractSetting implements VcsShowSettingOption {
  private boolean myValue = true;


  public VcsShowOptionsSettingImpl(final String displayName) {
    super(displayName);
  }

  public VcsShowOptionsSettingImpl(VcsConfiguration.StandardOption option) {
    this(option.getId());
  }

  public boolean getValue(){
    return myValue;
  }

  public void setValue(final boolean value) {
    myValue = value;
  }

}
