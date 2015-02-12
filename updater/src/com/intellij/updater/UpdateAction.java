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
      writeExecutableFlag(patchOutput, newerFile);
      writeDiff(olderFile, newerFile, patchOutput);
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
      boolean executable = readExecutableFlag(in);

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
      Utils.setExecutable(updated, executable);
    } else {
      updated = source;
    }
    replaceUpdated(updated, toFile);
  }
}
