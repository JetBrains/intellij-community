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
package com.intellij.updater;

import com.intellij.openapi.util.io.FileUtil;

import java.io.File;

@SuppressWarnings("ResultOfMethodCallIgnored")
public abstract class PatchTestCase extends UpdaterTestCase {
  protected File myNewerDir;
  protected File myOlderDir;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myOlderDir = getDataDir();
    myNewerDir = getTempFile("newDir");
    FileUtil.copyDir(myOlderDir, myNewerDir);

    FileUtil.delete(new File(myNewerDir, "bin/idea.bat"));
    FileUtil.writeToFile(new File(myNewerDir, "Readme.txt"), "hello");
    File newFile = new File(myNewerDir, "newDir/newFile.txt");
    newFile.getParentFile().mkdirs();
    newFile.createNewFile();
    FileUtil.writeToFile(newFile, "hello");

    FileUtil.delete(new File(myOlderDir, "lib/annotations_changed.jar"));
    FileUtil.delete(new File(myNewerDir, "lib/annotations.jar"));
    FileUtil.rename(new File(myNewerDir, "lib/annotations_changed.jar"),
                    new File(myNewerDir, "lib/annotations.jar"));

    FileUtil.delete(new File(myOlderDir, "lib/bootstrap_deleted.jar"));
    FileUtil.delete(new File(myNewerDir, "lib/bootstrap.jar"));
    FileUtil.rename(new File(myNewerDir, "lib/bootstrap_deleted.jar"),
                    new File(myNewerDir, "lib/bootstrap.jar"));

    FileUtil.delete(new File(myOlderDir, "lib/boot2_changed_with_unchanged_content.jar"));
    FileUtil.delete(new File(myNewerDir, "lib/boot2.jar"));
    FileUtil.rename(new File(myNewerDir, "lib/boot2_changed_with_unchanged_content.jar"),
                    new File(myNewerDir, "lib/boot2.jar"));
  }
}