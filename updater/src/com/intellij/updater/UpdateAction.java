package com.intellij.updater;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class UpdateAction extends BaseUpdateAction {
  public UpdateAction(String path, long checksum) {
    super(path, checksum);
  }

  public UpdateAction(DataInputStream in) throws IOException {
    super(in);
  }

  @Override
  protected void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
    patchOutput.putNextEntry(new ZipEntry(myPath));
    writeExecutableFlag(patchOutput, newerFile);
    writeDiff(olderFile, newerFile, patchOutput);
    patchOutput.closeEntry();
  }

  @Override
  protected void doApply(ZipFile patchFile, File toFile) throws IOException {
    InputStream in = Utils.findEntryInputStream(patchFile, myPath);
    boolean executable = readExecutableFlag(in);

    File temp = Utils.createTempFile();
    OutputStream out = new BufferedOutputStream(new FileOutputStream(temp));
    try {
      InputStream oldFileIn = new FileInputStream(toFile);
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

    replaceUpdated(temp, toFile);
    Utils.setExecutable(toFile, executable);
  }
}
