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

public class UpdateAction extends BaseUpdateAction {
  public UpdateAction(Patch patch, String path, long checksum) {
    this(patch, path, path, checksum, false);
  }

  public UpdateAction(Patch patch, String path, String source, long checksum, boolean move) {
    super(patch, path, source, checksum, move);
  }

  public UpdateAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
  }

  @Override
  protected void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
    if (!isMove()) {
      patchOutput.putNextEntry(new ZipEntry(getPath()));

      FileType type = getFileType(newerFile);
      if (type == FileType.SYMLINK) throw new IOException("Unexpected symlink: " + newerFile);
      writeFileType(patchOutput, type);
      writeDiff(olderFile, newerFile, patchOutput);

      patchOutput.closeEntry();
    }
  }

  @Override
  protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
    Runner.logger().info("Update action. File: " + toFile.getAbsolutePath());

    File source = getSource(backupDir);
    if (!isMove()) {
      try (InputStream in = Utils.findEntryInputStream(patchFile, getPath())) {
        if (in == null) {
          throw new IOException("Invalid entry " + getPath());
        }

        FileType type = readFileType(in);
        File tempFile = Utils.getTempFile(toFile.getName());
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile));
             InputStream oldFileIn = Utils.newFileInputStream(source, myPatch.isNormalized())) {
          applyDiff(in, oldFileIn, out);
        }

        if (type == FileType.EXECUTABLE_FILE) {
          Utils.setExecutable(tempFile);
        }

        source = tempFile;
      }
    }

    replaceUpdated(source, toFile);
  }
}