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
    FileUtil.writeToFile(new File(myNewerDir, "Readme.txt"), "hello".getBytes());
    File newFile = new File(myNewerDir, "newDir/newFile.txt");
    newFile.getParentFile().mkdirs();
    newFile.createNewFile();
    FileUtil.writeToFile(newFile, "hello".getBytes());

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