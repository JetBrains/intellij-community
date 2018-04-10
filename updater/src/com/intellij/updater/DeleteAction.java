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

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class DeleteAction extends PatchAction {
  public DeleteAction(Patch patch, String path, long checksum) {
    super(patch, path, checksum);
  }

  public DeleteAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
  }

  @Override
  public void doBuildPatchFile(File olderDir, File newerFile, ZipOutputStream patchOutput) {
    // do nothing
  }

  @Override
  public ValidationResult validate(File toDir) throws IOException {
    File toFile = getFile(toDir);
    ValidationResult result = doValidateAccess(toFile, ValidationResult.Action.DELETE, false);
    if (result != null) return result;

    if (myPatch.validateDeletion(getPath()) && toFile.exists() && isModified(toFile)) {
      ValidationResult.Option[] options = myPatch.isStrict()
                                          ? new ValidationResult.Option[]{ValidationResult.Option.DELETE}
                                          : new ValidationResult.Option[]{ValidationResult.Option.DELETE, ValidationResult.Option.KEEP};
      boolean invalid = getChecksum() == Digester.INVALID;
      ValidationResult.Action action = invalid ? ValidationResult.Action.VALIDATE : ValidationResult.Action.DELETE;
      String message = invalid ? "Unexpected file" : "Modified";
      return new ValidationResult(ValidationResult.Kind.CONFLICT, getPath(), action, message, options);
    }

    return null;
  }

  @Override
  protected boolean doShouldApply(File toDir) {
    return getFile(toDir).exists();
  }

  @Override
  protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
    Runner.logger().info("Delete action. File: " + toFile.getAbsolutePath());
    //NOTE: a folder can be deleted only in case if it does not contain any user's files/folders.
    String[] children;
    if (!toFile.isDirectory() || (children = toFile.list()) != null && children.length == 0) {
      Runner.logger().info("Delete: " + toFile.getAbsolutePath());
      Utils.delete(toFile);
    }
  }

  @Override
  protected void doBackup(File toFile, File backupFile) throws IOException {
    Utils.copy(toFile, backupFile);
  }

  @Override
  protected void doRevert(File toFile, File backupFile) throws IOException {
    if (!toFile.exists() || toFile.isDirectory() || isModified(toFile)) {
      Utils.delete(toFile); // make sure there is no directory remained on this path (may remain from previous 'create' actions
      Utils.copy(backupFile, toFile);
    }
  }
}