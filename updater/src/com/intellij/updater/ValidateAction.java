/*
 * Copyright (C) 2014 The Android Open Source Project
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
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ValidateAction extends PatchAction {
  // Only used on patch creation
  protected transient File myOlderDir;

  public ValidateAction(Patch patch, String path, long checksum) {
    super(patch, path, checksum);
  }

  public ValidateAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
  }

  @Override
  protected void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
  }

  @Override
  public ValidationResult validate(File toDir) throws IOException {
    return doValidateNotChanged(getFile(toDir), ValidationResult.Kind.ERROR, ValidationResult.Action.VALIDATE);
  }

  @Override
  protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
  }

  @Override
  protected void doBackup(File toFile, File backupFile) throws IOException {
  }

  @Override
  protected void doRevert(File toFile, File backupFile) throws IOException {
  }
}
