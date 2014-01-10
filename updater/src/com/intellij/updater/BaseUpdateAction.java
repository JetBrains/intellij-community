package com.intellij.updater;

import ie.wombat.jbdiff.JBDiff;
import ie.wombat.jbdiff.JBPatch;

import java.io.*;
import java.util.zip.ZipOutputStream;

public abstract class BaseUpdateAction extends PatchAction {
  public BaseUpdateAction(String path, long checksum) {
    super(path, checksum);
  }

  public BaseUpdateAction(DataInputStream in) throws IOException {
    super(in);
  }

  @Override
  protected ValidationResult doValidate(File toFile) throws IOException {
    ValidationResult result = doValidateAccess(toFile, ValidationResult.Action.UPDATE);
    if (result != null) return result;
    return doValidateNotChanged(toFile, ValidationResult.Kind.ERROR, ValidationResult.Action.UPDATE);
  }

  @Override
  protected boolean shouldApplyOn(File toFile) {
    // if the file is optional in may not exist
    return toFile.exists();
  }

  @Override
  protected void doBackup(File toFile, File backupFile) throws IOException {
    Utils.copy(toFile, backupFile);
    Runner.logger.trace("toFile " + toFile.getCanonicalPath() + " backupFile " + backupFile.getCanonicalPath());
  }

  protected void replaceUpdated(File from, File dest) throws IOException {
    // on OS X code signing caches seem to be associated with specific file ids, so we need to remove the original file.
    if (!dest.delete()) throw new IOException("Cannot delete file " + dest);
    Utils.copy(from, dest);
    Runner.logger.trace("from " + from.getCanonicalPath() + " dest " + dest.getCanonicalPath());
  }

  @Override
  protected void doRevert(File toFile, File backupFile) throws IOException {
    if (!toFile.exists() || isModified(toFile)) {
      Utils.copy(backupFile, toFile);
      Runner.logger.trace("backupFile " + backupFile.getCanonicalPath() + " toFile " + toFile.getCanonicalPath());
    }
  }

  protected void writeDiff(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
    Runner.logger.trace("writing diff");
    BufferedInputStream olderFileIn = new BufferedInputStream(new FileInputStream(olderFile));
    BufferedInputStream newerFileIn = new BufferedInputStream(new FileInputStream(newerFile));
    try {
      writeDiff(olderFileIn, newerFileIn, patchOutput);
    }
    finally {
      olderFileIn.close();
      newerFileIn.close();
    }
  }

  protected void writeDiff(InputStream olderFileIn, InputStream newerFileIn, ZipOutputStream patchOutput)
    throws IOException {
    Runner.logger.trace("writing diff");
    ByteArrayOutputStream diffOutput = new ByteArrayOutputStream();
    byte[] newerFileBuffer = JBDiff.bsdiff(olderFileIn, newerFileIn, diffOutput);
    diffOutput.close();

    if (diffOutput.size() < newerFileBuffer.length) {
      patchOutput.write(1);
      Utils.copyBytesToStream(diffOutput, patchOutput);
    }
    else {
      patchOutput.write(0);
      Utils.copyBytesToStream(newerFileBuffer, patchOutput);
    }
  }

  protected void applyDiff(InputStream patchInput, InputStream oldFileIn, OutputStream toFileOut) throws IOException {
    Runner.logger.trace("applying diff");
    if (patchInput.read() == 1) {
      JBPatch.bspatch(oldFileIn, toFileOut, patchInput);
    }
    else {
      Utils.copyStream(patchInput, toFileOut);
    }
  }
}
