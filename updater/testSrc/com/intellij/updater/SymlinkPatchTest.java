// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class SymlinkPatchTest extends PatchTestCase {
  @Override
  public void setUp() throws Exception {
    assumeTrue(!UtilsTest.IS_WINDOWS);

    super.setUp();

    FileUtil.writeToFile(new File(myOlderDir, "Readme.txt"), "hello");
    resetNewerDir();
  }

  @Test
  public void same() throws Exception {
    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));
    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.link"));
    assertThat(createPatch().getActions()).containsExactly();
  }

  @Test
  public void create() throws Exception {
    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.link"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void delete() throws Exception {
    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.link", CHECKSUMS.LINK_TO_README_TXT));
  }

  @Test
  public void rename() throws Exception {
    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.lnk"));
    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.link"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.lnk", CHECKSUMS.LINK_TO_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void retarget() throws Exception {
    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));
    Utils.createLink("./Readme.txt", new File(myNewerDir, "Readme.link"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.link", CHECKSUMS.LINK_TO_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void renameAndRetarget() throws Exception {
    Utils.createLink("./Readme.txt", new File(myOlderDir, "Readme.lnk"));
    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.link"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.lnk", CHECKSUMS.LINK_TO_DOT_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }
}