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

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class CreateAction extends PatchAction {
  public CreateAction(Patch patch, String path) {
    super(patch, path, Digester.INVALID);
  }

  public CreateAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
  }

  @Override
  protected void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
    Runner.logger().info("building PatchFile");
    patchOutput.putNextEntry(new ZipEntry(myPath));
    if (!newerFile.isDirectory()) {
      if (Utils.isLink(newerFile)) {
        writeLinkInfo(newerFile, patchOutput);
      } else {
        writeExecutableFlag(patchOutput, newerFile);
        Utils.copyFileToStream(newerFile, patchOutput);
      }
    }

    patchOutput.closeEntry();
  }

  @Override
  public ValidationResult validate(File toDir) {
    File toFile = getFile(toDir);
    ValidationResult result = doValidateAccess(toFile, ValidationResult.Action.CREATE);
    if (result != null) return result;

    if (toFile.exists()) {
      ValidationResult.Option[] options = myPatch.isStrict()
                                          ? new ValidationResult.Option[]{ValidationResult.Option.REPLACE}
                                          : new ValidationResult.Option[]{ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP};
      return new ValidationResult(ValidationResult.Kind.CONFLICT, myPath,
                                  ValidationResult.Action.CREATE,
                                  ValidationResult.ALREADY_EXISTS_MESSAGE,
                                  options);
    }
    return null;
  }

  @Override
  protected boolean isModified(File toFile) throws IOException {
    return false;
  }

  @Override
  protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
    prepareToWriteFile(toFile);

    ZipEntry entry = Utils.getZipEntry(patchFile, myPath);
    if (entry.isDirectory()) {
      if (!toFile.mkdir()) {
        throw new IOException("Unable to create directory " + myPath);
      }
    } else {
      InputStream in = Utils.findEntryInputStreamForEntry(patchFile, entry);
      try {
        int filePermissions = in.read();
        if (filePermissions > 1 ) {
          Utils.createLink(readLinkInfo(in, filePermissions), toFile);
        }
        else {
          Utils.copyStreamToFile(in, toFile);
          Utils.setExecutable(toFile, filePermissions == 1 );
        }
      }
      finally {
        in.close();
      }
    }
  }

  private static void prepareToWriteFile(File file) throws IOException {
    if (file.exists()) {
      Utils.delete(file);
      return;
    }

    while (file != null && !file.exists()) {
      file = file.getParentFile();
    }
    if (file != null && !file.isDirectory()) {
      Utils.delete(file);
    }
  }

  protected void doBackup(File toFile, File backupFile) {
    // do nothing
  }

  protected void doRevert(File toFile, File backupFile) throws IOException {
    Utils.delete(toFile);
  }
}