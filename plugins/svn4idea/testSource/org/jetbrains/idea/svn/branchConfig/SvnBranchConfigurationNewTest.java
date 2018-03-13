// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class SvnBranchConfigurationNewTest {

  public static final InfoStorage<List<SvnBranchItem>> EMPTY_INFO = new InfoStorage<>(new ArrayList<>(), InfoReliability.empty);

  @Test
  public void getRelativeUrlMatchesLongestPrefix() {
    SvnBranchConfigurationNew branchConfig = new SvnBranchConfigurationNew();
    branchConfig.setTrunkUrl("svn://example.com/trunk");
    branchConfig.addBranches("svn://example.com/branches", EMPTY_INFO);
    branchConfig.addBranches("svn://example.com/branches/subbranches", EMPTY_INFO);

    assertThat(branchConfig.getRelativeUrl("svn://example.com/branches/subbranches/someBranch/dir"), is("/dir"));
  }
}