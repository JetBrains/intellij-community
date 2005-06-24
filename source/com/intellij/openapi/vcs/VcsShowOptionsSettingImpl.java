package com.intellij.openapi.vcs;

import java.util.Collection;
import java.util.HashSet;

public class VcsShowOptionsSettingImpl implements VcsShowSettingOption {
  private boolean myValue;
  private final String myDisplayName;
  private final Collection<AbstractVcs> myApplicable = new HashSet<AbstractVcs>();


  public VcsShowOptionsSettingImpl(final String displayName) {
    myDisplayName = displayName;
  }

  public boolean getValue(){
    return myValue;
  }

  public void setValue(final boolean value) {
    myValue = value;
  }

  public String getDisplayName(){
    return myDisplayName;
  }

  public void addApplicableVcs(AbstractVcs vcs) {
    myApplicable.add(vcs);
  }

  public boolean isApplicableTo(AbstractVcs vcs) {
    return myApplicable.contains(vcs);
  }
}
