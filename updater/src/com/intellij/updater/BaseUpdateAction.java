package com.intellij.updater;

import ie.wombat.jbdiff.JBDiff;
import ie.wombat.jbdiff.JBPatch;

import java.io.*;
import java.util.zip.ZipOutputStream;

public abstract class BaseUpdateAction extends PatchAction {
  private final String mySource;
  protected final boolean myIsMove;

  public BaseUpdateAction(Patch patch, String path, String source, long checksum, boolean move) {
    super(patch, path, checksum);
    myIsMove = move;
    mySource = source;
  }

  public BaseUpdateAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
    mySource = in.readUTF();
    myIsMove = in.readBoolean();
  }

  @Override
  public void write(DataOutputStream out) throws IOException {
    super.write(out);
    out.writeUTF(mySource);
    out.writeBoolean(myIsMove);
  }

  protected File getSource(File toDir) {
    return new File(toDir, mySource);
  }

  public String getSourcePath() {
    return mySource;
  }

  @Override
  protected boolean doShouldApply(File toDir) {
    // if the file is optional in may not exist
    return getSource(toDir).exists();
  }

  @Override
  public void buildPatchFile(File olderDir, File newerDir, ZipOutputStream patchOutput) throws IOException {
    doBuildPatchFile(getSource(olderDir), getFile(newerDir), patchOutput);
  }

  @Override
  public ValidationResult validate(File toDir) throws IOException {
    File fromFile = getSource(toDir);
    ValidationResult result = doValidateAccess(fromFile, ValidationResult.Action.UPDATE);
    if (result != null) return result;
    if (!mySource.isEmpty()) {
      result = doValidateAccess(getFile(toDir), ValidationResult.Action.UPDATE);
      if (result != null) return result;
    }
    return doValidateNotChanged(fromFile, ValidationResult.Kind.ERROR, ValidationResult.Action.UPDATE);
  }

  @Override
  protected void doBackup(File toFile, File backupFile) throws IOException {
    Utils.mirror(toFile, backupFile);
  }

  protected void replaceUpdated(File from, File dest) throws IOException {
    // on OS X code signing caches seem to be associated with specific file ids, so we need to remove the original file.
    if (dest.exists() && !dest.delete()) throw new IOException("Cannot delete file " + dest);
    Utils.copy(from, dest);
  }

  @Override
  protected void doRevert(File toFile, File backupFile) throws IOException {
    if (!toFile.exists() || isModified(toFile)) {
      Utils.mirror(backupFile, toFile);
    }
  }

  protected void writeDiff(File olderFile, File newerFile, OutputStream patchOutput) throws IOException {
    BufferedInputStream olderFileIn = new BufferedInputStream(Utils.newFileInputStream(olderFile, myPatch.isNormalized()));
    BufferedInputStream newerFileIn = new BufferedInputStream(new FileInputStream(newerFile));
    try {
      writeDiff(olderFileIn, newerFileIn, patchOutput);
    }
    finally {
      olderFileIn.close();
      newerFileIn.close();
    }
  }

  protected void writeDiff(InputStream olderFileIn, InputStream newerFileIn, OutputStream patchOutput)
    throws IOException {
    Runner.logger.info("writing diff");
    ByteArrayOutputStream diffOutput = new ByteArrayOutputStream();
    byte[] newerFileBuffer = JBDiff.bsdiff(olderFileIn, newerFileIn, diffOutput);
    diffOutput.close();

    if (!isCritical() && diffOutput.size() < newerFileBuffer.length) {
      patchOutput.write(1);
      Utils.copyBytesToStream(diffOutput, patchOutput);
    }
    else {
      patchOutput.write(0);
      Utils.copyBytesToStream(newerFileBuffer, patchOutput);
    }
  }

  protected void applyDiff(InputStream patchInput, InputStream oldFileIn, OutputStream toFileOut) throws IOException {
    if (patchInput.read() == 1) {
      JBPatch.bspatch(oldFileIn, toFileOut, patchInput);
    }
    else {
      Utils.copyStream(patchInput, toFileOut);
    }
  }

  @Override
  public String toString() {
    String moveInfo = "";
    if (!mySource.equals(myPath)) {
      moveInfo = "[" + (myIsMove ? "= " : "~ ") + mySource + "]";
    }
    return super.toString() + moveInfo;
  }

  public boolean isMove() {
    return myIsMove;
  }
}
