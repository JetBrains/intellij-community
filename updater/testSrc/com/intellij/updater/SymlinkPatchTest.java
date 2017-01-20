/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
    Utils.delete(myNewerDir);
    FileUtil.copyDir(myOlderDir, myNewerDir);
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