// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.idea.svn.branchConfig;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default constructor and setters are necessary for serialization purposes.
 */
@SuppressWarnings("UnusedDeclaration")
public class SvnBranchConfiguration {
  private String myTrunkUrl;
  private @NotNull List<String> myBranchUrls;
  private boolean myUserinfoInUrl;

  public SvnBranchConfiguration() {
    myBranchUrls = new ArrayList<>();
  }

  public SvnBranchConfiguration(String trunkUrl, @NotNull List<String> branchUrls, boolean userinfoInUrl) {
    myTrunkUrl = trunkUrl;
    myBranchUrls = branchUrls;
    Collections.sort(myBranchUrls);
    myUserinfoInUrl = userinfoInUrl;
  }

  public boolean isUserinfoInUrl() {
    return myUserinfoInUrl;
  }

  public void setUserinfoInUrl(final boolean userinfoInUrl) {
    myUserinfoInUrl = userinfoInUrl;
  }

  public void setBranchUrls(@NotNull List<String> branchUrls) {
    myBranchUrls = branchUrls;
    Collections.sort(myBranchUrls);
  }

  public void setTrunkUrl(final String trunkUrl) {
    myTrunkUrl = trunkUrl;
  }

  public String getTrunkUrl() {
    return myTrunkUrl;
  }

  public @NotNull List<String> getBranchUrls() {
    return myBranchUrls;
  }
}
