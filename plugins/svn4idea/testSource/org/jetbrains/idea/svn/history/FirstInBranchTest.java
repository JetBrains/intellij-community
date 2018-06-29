// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.Svn17TestCase;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.junit.Test;

import java.io.IOException;

import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.SvnUtil.getHeadRevision;
import static org.jetbrains.idea.svn.commandLine.CommandUtil.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FirstInBranchTest extends Svn17TestCase {

  private SvnVcs myVcs;
  private Url myTrunkUrl;
  private Url myBranchesUrl;
  private long myHeadRevision;
  private Url myRepositoryUrl;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myVcs = SvnVcs.getInstance(myProject);
    myRepositoryUrl = createUrl(myRepoUrl);
    myTrunkUrl = myRepositoryUrl.appendPath("trunk", true);
    myBranchesUrl = myRepositoryUrl.appendPath("branches", true);

    runInAndVerifyIgnoreOutput("mkdir", "-m", "trunk", myTrunkUrl.toString());
    runInAndVerifyIgnoreOutput("mkdir", "-m", "branches", myBranchesUrl.toString());

    myHeadRevision = getHeadRevision(myVcs, myRepositoryUrl).getNumber();
  }

  @Test
  public void parent_branch_is_copied_from_trunk() throws Exception {
    Url parentBranchUrl = createBranch("parent_branch", myTrunkUrl);

    assertBranchPoint(myTrunkUrl, parentBranchUrl, myHeadRevision, myHeadRevision + 1);
  }

  @Test
  public void child_branch_is_copied_from_trunk() throws Exception {
    Url parentBranchUrl = createBranch("parent_branch", myTrunkUrl);
    Url childBranchUrl = createBranch("child_branch", parentBranchUrl);

    assertBranchPoint(myTrunkUrl, childBranchUrl, myHeadRevision, myHeadRevision + 1);
  }

  @Test
  public void earlier_parent_branch_is_copied_from_latter() throws Exception {
    Url parent1BranchUrl = createBranch("parent1_branch", myTrunkUrl);
    Url parent2BranchUrl = createBranch("parent2_branch", myTrunkUrl);

    assertBranchPoint(parent2BranchUrl, parent1BranchUrl, myHeadRevision, myHeadRevision + 1);
  }

  @Test
  public void latter_parent_branch_is_copied_from_earlier_if_same_copy_revisions() throws Exception {
    Target source = Target.on(myTrunkUrl, Revision.of(myHeadRevision));
    Url parent1BranchUrl = createBranch("parent1_branch", source);
    Url parent2BranchUrl = createBranch("parent2_branch", source);

    assertBranchPoint(parent1BranchUrl, parent2BranchUrl, myHeadRevision, myHeadRevision + 2);
  }

  @Test
  public void other_child_branch_is_copied_from_latter_parent_branch() throws Exception {
    Url parent1BranchUrl = createBranch("parent1_branch", myTrunkUrl);
    Url parent2BranchUrl = createBranch("parent2_branch", myTrunkUrl);
    Url child1BranchUrl = createBranch("child1_branch", parent1BranchUrl);

    assertBranchPoint(parent2BranchUrl, child1BranchUrl, myHeadRevision, myHeadRevision + 1);
  }

  @Test
  public void child_branch_with_earlier_parent_is_copied_from_other_child() throws Exception {
    Url parent1BranchUrl = createBranch("parent1_branch", myTrunkUrl);
    Url parent2BranchUrl = createBranch("parent2_branch", myTrunkUrl);
    Url child1BranchUrl = createBranch("child1_branch", parent1BranchUrl);
    Url child2BranchUrl = createBranch("child2_branch", parent2BranchUrl);

    assertBranchPoint(child2BranchUrl, child1BranchUrl, myHeadRevision, myHeadRevision + 1);
  }

  @NotNull
  private Url createBranch(@NotNull String branchName, @NotNull Url source) throws IOException, SvnBindException {
    return createBranch(branchName, Target.on(source));
  }

  @NotNull
  private Url createBranch(@NotNull String branchName, @NotNull Target source) throws IOException, SvnBindException {
    Url branchUrl = myBranchesUrl.appendPath(branchName, true);

    runInAndVerifyIgnoreOutput("copy", "-m", branchName, format(source.getPath(), source.getPegRevision()), branchUrl.toString());

    return branchUrl;
  }

  private void assertBranchPoint(@NotNull Url sourceUrl, @NotNull Url targetUrl, long sourceRevision, long targetRevision)
    throws Exception {
    CopyData branchTrunk = new FirstInBranch(myVcs, myRepositoryUrl, targetUrl, sourceUrl).run();
    assertBranchPoint(branchTrunk, sourceRevision, targetRevision, true);

    CopyData trunkBranch = new FirstInBranch(myVcs, myRepositoryUrl, sourceUrl, targetUrl).run();
    assertBranchPoint(trunkBranch, sourceRevision, targetRevision, false);
  }

  private static void assertBranchPoint(@Nullable CopyData point, long sourceRevision, long targetRevision, boolean isTrunkFromBranch) {
    assertNotNull(point);
    assertEquals(sourceRevision, point.getCopySourceRevision());
    assertEquals(targetRevision, point.getCopyTargetRevision());
    assertEquals(isTrunkFromBranch, point.isTrunkSupposedCorrect());
  }
}