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

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class UpdateAction extends BaseUpdateAction {
  public UpdateAction(Patch patch, String path, String source, long checksum, boolean move) {
    super(patch, path, source, checksum, move);
  }

  public UpdateAction(Patch patch, String path, long checksum) {
    this(patch, path, path, checksum, false);
  }

  public UpdateAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
  }

  @Override
  protected void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
    if (!myIsMove) {
      patchOutput.putNextEntry(new ZipEntry(myPath));
      if (Utils.isLink(newerFile)) {
        writeLinkInfo(newerFile, patchOutput);
      } else {
        writeExecutableFlag(patchOutput, newerFile);
        writeDiff(olderFile, newerFile, patchOutput);
      }
      patchOutput.closeEntry();
    }
  }

  @Override
  protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
    File source = getSource(backupDir);
    File updated;
    if (!myIsMove) {
      updated = Utils.createTempFile();
      InputStream in = Utils.findEntryInputStream(patchFile, myPath);
      int filePermissions = in.read();
      if (filePermissions > 1 ) {
        Utils.createLink(readLinkInfo(in, filePermissions), toFile);
        in.close();
        return;
      } else {
        OutputStream out = new BufferedOutputStream(new FileOutputStream(updated));
        try {
          InputStream oldFileIn = Utils.newFileInputStream(source, myPatch.isNormalized());
          try {
            applyDiff(in, oldFileIn, out);
          }
          finally {
            oldFileIn.close();
          }
        }
        finally {
          out.close();
        }
      }
      Utils.setExecutable(updated, filePermissions == 1);
    } else {
      updated = source;
    }
    replaceUpdated(updated, toFile);
  }
}