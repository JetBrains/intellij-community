/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class RenameRootDirectoryAction extends PatchAction {

  private final String myOldDirectoryName;
  private final String myNewDirectoryName;
  private boolean myDirectoryRenamed;

  public RenameRootDirectoryAction(Patch patch, String oldDirectoryName, String newDirectoryName) {
    super(patch, oldDirectoryName, Digester.INVALID);
    myOldDirectoryName = oldDirectoryName;
    myNewDirectoryName = newDirectoryName;
    myDirectoryRenamed = false;
  }

  public RenameRootDirectoryAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
    myOldDirectoryName = in.readUTF();
    myNewDirectoryName = in.readUTF();
  }

  @Override
  protected void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
    // do nothing
  }

  @Override
  protected boolean doShouldApply(File toDir) {
    // Run the action only if the directory name is the same as the one for which patch was created. If user moved the product or
    //   specified custom installation directory, the action will not be run.
    return toDir.getName().equals(myOldDirectoryName);
  }

  @Override
  protected ValidationResult validate(File toDir) throws IOException {
    // No validation needed. This action should not fail application of the patch, so doApply() will ignore any error conditions.
    return null;
  }

  @Override
  protected File getFile(File baseDir) {
    return baseDir;
  }

  @Override
  protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
    Path sourcePath = toFile.toPath();
    Path targetPath = toFile.toPath().resolveSibling(myNewDirectoryName);

    Runner.logger().info("Rename root directory action: from " + sourcePath + " to " + targetPath);

    if (Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
      Runner.logger().info("Rename root directory action: Skipped (target path exists)");
      return;
    }

    try {
      Files.move(sourcePath, targetPath);
      myDirectoryRenamed = true;
    }
    catch (IOException | SecurityException e) {
      Runner.logger().info("Rename root directory action: Skipped (" + e + ")");
    }
  }

  @Override
  protected void doBackup(File toFile, File backupFile) throws IOException {
    // do nothing
  }

  @Override
  protected void doRevert(File toFile, File backupFile) throws IOException {
    if (!myDirectoryRenamed || toFile.exists()) {
      return;
    }
    Path sourcePath = toFile.toPath().resolveSibling(myNewDirectoryName);
    Path targetPath = toFile.toPath();

    Files.move(sourcePath, targetPath);
  }

  @Override
  public void write(DataOutputStream out) throws IOException {
    super.write(out);

    out.writeUTF(myOldDirectoryName);
    out.writeUTF(myNewDirectoryName);
  }
}
