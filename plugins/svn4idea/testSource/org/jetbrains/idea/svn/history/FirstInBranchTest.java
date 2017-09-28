/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.history;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.Svn17TestCase;
import org.jetbrains.idea.svn.SvnVcs;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNURL;

import java.io.IOException;

import static org.jetbrains.idea.svn.SvnUtil.createUrl;
import static org.jetbrains.idea.svn.SvnUtil.getHeadRevision;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FirstInBranchTest extends Svn17TestCase {

  private SvnVcs myVcs;
  private String myTrunkUrl;
  private String myBranchesUrl;
  private long myHeadRevision;
  private SVNURL myRepositoryUrl;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myVcs = SvnVcs.getInstance(myProject);
    myTrunkUrl = myRepoUrl + "/trunk";
    myBranchesUrl = myRepoUrl + "/branches";

    runInAndVerifyIgnoreOutput("mkdir", "-m", "trunk", myTrunkUrl);
    runInAndVerifyIgnoreOutput("mkdir", "-m", "branches", myBranchesUrl);

    myRepositoryUrl = createUrl(myRepoUrl);
    myHeadRevision = getHeadRevision(myVcs, myRepositoryUrl).getNumber();
  }

  @Test
  public void parent_branch_is_copied_from_trunk() throws Exception {
    String parentBranchUrl = createBranch("parent_branch", myTrunkUrl);

    assertBranchPoint(myTrunkUrl, parentBranchUrl, myHeadRevision, myHeadRevision + 1);
  }

  @Test
  public void child_branch_is_copied_from_trunk() throws Exception {
    String parentBranchUrl = createBranch("parent_branch", myTrunkUrl);
    String childBranchUrl = createBranch("child_branch", parentBranchUrl);

    assertBranchPoint(myTrunkUrl, childBranchUrl, myHeadRevision, myHeadRevision + 1);
  }

  @Test
  public void earlier_parent_branch_is_copied_from_latter() throws Exception {
    String parent1BranchUrl = createBranch("parent1_branch", myTrunkUrl);
    String parent2BranchUrl = createBranch("parent2_branch", myTrunkUrl);

    assertBranchPoint(parent2BranchUrl, parent1BranchUrl, myHeadRevision, myHeadRevision + 1);
  }

  @Test
  public void latter_parent_branch_is_copied_from_earlier_if_same_copy_revisions() throws Exception {
    String parent1BranchUrl = createBranch("parent1_branch", myTrunkUrl + "@" + myHeadRevision);
    String parent2BranchUrl = createBranch("parent2_branch", myTrunkUrl + "@" + myHeadRevision);

    assertBranchPoint(parent1BranchUrl, parent2BranchUrl, myHeadRevision, myHeadRevision + 2);
  }

  @Test
  public void other_child_branch_is_copied_from_latter_parent_branch() throws Exception {
    String parent1BranchUrl = createBranch("parent1_branch", myTrunkUrl);
    String parent2BranchUrl = createBranch("parent2_branch", myTrunkUrl);
    String child1BranchUrl = createBranch("child1_branch", parent1BranchUrl);

    assertBranchPoint(parent2BranchUrl, child1BranchUrl, myHeadRevision, myHeadRevision + 1);
  }

  @Test
  public void child_branch_with_earlier_parent_is_copied_from_other_child() throws Exception {
    String parent1BranchUrl = createBranch("parent1_branch", myTrunkUrl);
    String parent2BranchUrl = createBranch("parent2_branch", myTrunkUrl);
    String child1BranchUrl = createBranch("child1_branch", parent1BranchUrl);
    String child2BranchUrl = createBranch("child2_branch", parent2BranchUrl);

    assertBranchPoint(child2BranchUrl, child1BranchUrl, myHeadRevision, myHeadRevision + 1);
  }

  @NotNull
  private String createBranch(@NotNull String branchName, @NotNull String sourceUrl) throws IOException {
    String branchUrl = myBranchesUrl + "/" + branchName;

    runInAndVerifyIgnoreOutput("copy", "-m", branchName, sourceUrl, branchUrl);

    return branchUrl;
  }

  private void assertBranchPoint(@NotNull String sourceUrl, @NotNull String targetUrl, long sourceRevision, long targetRevision)
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