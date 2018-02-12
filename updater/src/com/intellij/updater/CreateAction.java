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

import java.io.*;
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
    patchOutput.putNextEntry(new ZipEntry(getPath()));

    if (!newerFile.isDirectory()) {
      FileType type = getFileType(newerFile);
      writeFileType(patchOutput, type);
      if (type == FileType.SYMLINK) {
        writeLinkInfo(newerFile, patchOutput);
      }
      else {
        Utils.copyFileToStream(newerFile, patchOutput);
      }
    }

    patchOutput.closeEntry();
  }

  private static void writeLinkInfo(File file, OutputStream out) throws IOException {
    String target = Utils.readLink(file);
    if (target.isEmpty()) throw new IOException("Invalid link: " + file);
    byte[] bytes = target.getBytes("UTF-8");
    out.write(bytes.length);
    out.write(bytes);
  }

  @Override
  public ValidationResult validate(File toDir) {
    File toFile = getFile(toDir);
    ValidationResult result = doValidateAccess(toFile, ValidationResult.Action.CREATE, true);
    if (result != null) return result;

    if (toFile.exists()) {
      ValidationResult.Option[] options = myPatch.isStrict()
                                          ? new ValidationResult.Option[]{ValidationResult.Option.REPLACE}
                                          : new ValidationResult.Option[]{ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP};
      return new ValidationResult(ValidationResult.Kind.CONFLICT, getPath(),
                                  ValidationResult.Action.CREATE,
                                  ValidationResult.ALREADY_EXISTS_MESSAGE,
                                  options);
    }
    return null;
  }

  @Override
  protected boolean isModified(File toFile) {
    return false;
  }

  @Override
  protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
    Runner.logger().info("Create action. File: " + toFile.getAbsolutePath());
    prepareToWriteFile(toFile);

    ZipEntry entry = Utils.getZipEntry(patchFile, getPath());
    if (entry.isDirectory()) {
      if (!toFile.mkdir()) {
        throw new IOException("Unable to create directory " + getPath());
      }
    }
    else {
      try (InputStream in = Utils.findEntryInputStreamForEntry(patchFile, entry)) {
        if (in == null) {
          throw new IOException("Invalid entry " + getPath());
        }

        FileType type = readFileType(in);
        if (type == FileType.SYMLINK) {
          Utils.createLink(readLinkInfo(in), toFile);
        }
        else {
          Utils.copyStreamToFile(in, toFile);
          if (type == FileType.EXECUTABLE_FILE) {
            Utils.setExecutable(toFile);
          }
        }
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

  private static String readLinkInfo(InputStream in) throws IOException {
    int length = in.read();
    if (length <= 0) throw new IOException("Stream format error");
    byte[] bytes = Utils.readBytes(in, length);
    return new String(bytes, "UTF-8");
  }

  protected void doBackup(File toFile, File backupFile) {
    // do nothing
  }

  protected void doRevert(File toFile, File backupFile) throws IOException {
    Utils.delete(toFile);
  }
}