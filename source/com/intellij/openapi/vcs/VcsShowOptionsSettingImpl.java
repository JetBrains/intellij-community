package com.intellij.openapi.vcs;

import java.util.*;

public class VcsShowOptionsSettingImpl implements VcsShowSettingOption {
  private boolean myValue;
  private final String myDisplayName;
  private final Collection<AbstractVcs> myApplicable = new HashSet<AbstractVcs>();


  public VcsShowOptionsSettingImpl(final String displayName) {
    myDisplayName = displayName;
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

  public String getDisplayName(){
    return myDisplayName;
  }

  public void addApplicableVcs(AbstractVcs vcs) {
    myApplicable.add(vcs);
  }

  public boolean isApplicableTo(Collection<AbstractVcs> vcs) {
    for (AbstractVcs abstractVcs : vcs) {
      if (myApplicable.contains(abstractVcs)) return true;
    }
    return false;
  }

  public List<AbstractVcs> getApplicableVcses() {
    return new ArrayList<AbstractVcs>(myApplicable);
  }
}
