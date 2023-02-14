// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import com.intellij.updater.Utils.OpenByteArrayOutputStream;
import ie.wombat.jbdiff.JBDiff;
import ie.wombat.jbdiff.JBPatch;

import java.io.*;
import java.nio.file.Files;
import java.util.Objects;
import java.util.zip.ZipOutputStream;

import static com.intellij.updater.Runner.LOG;

/**
 * <p>
 *   <i>In-place update (standard mode)</i><br/>
 *   Backup: the file is copied to a temporary directory.<br/>
 *   Apply: the file is patched in-place.<br/>
 *   Revert: the file is copied from a temporary directory.<br/>
 * </p>
 * <p>
 *   <i>Move without a change</i><br/>
 *   Backup: - (the file does not exist; the source is backed by a companion DeleteAction).<br/>
 *   Apply: the file is copied from the companion backup.<br/>
 *   Revert: the file is removed (the source is restored by the companion action).<br/>
 * </p>
 * <p>
 *   <i>Move with a change</i><br/>
 *   Backup: - (the file does not exist; the source is backed by a companion DeleteAction).<br/>
 *   Apply: the file is copied from the companion backup and patched.<br/>
 *   Revert: the file is removed (the source is restored by the companion action).<br/>
 * </p>
 */
public abstract class BaseUpdateAction extends PatchAction {
  private static final byte RAW = 0;
  private static final byte COMPRESSED = 1;

  private final String mySource;
  private final boolean myIsMove;
  private final boolean myInPlace;

  public BaseUpdateAction(Patch patch, String path, String source, long checksum, boolean move) {
    super(patch, path, checksum);
    mySource = source;
    myIsMove = move;
    myInPlace = !myIsMove && mySource.equals(getPath());
  }

  public BaseUpdateAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
    mySource = in.readUTF();
    myIsMove = in.readBoolean();
    myInPlace = !myIsMove && mySource.equals(getPath());
  }

  @Override
  protected String getReportPath() {
    return mySource;
  }

  @Override
  public void write(DataOutputStream out) throws IOException {
    super.write(out);
    out.writeUTF(mySource);
    out.writeBoolean(myIsMove);
  }

  @Override
  public boolean calculate(File olderDir, File newerDir) throws IOException {
    return doCalculate(getSource(olderDir), getFile(newerDir));
  }

  protected File getSource(File toDir) {
    return new File(toDir, mySource);
  }

  protected boolean isMove() {
    return myIsMove;
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
    ValidationResult result = doValidateAccess(fromFile, ValidationResult.Action.UPDATE, true);
    if (result != null) return result;
    if (!mySource.isEmpty()) {
      result = doValidateAccess(getFile(toDir), ValidationResult.Action.UPDATE, true);
      if (result != null) return result;
    }
    return doValidateNotChanged(fromFile, ValidationResult.Action.UPDATE);
  }

  @Override
  public boolean mandatoryBackup() {
    return !myInPlace;
  }

  @Override
  public void backup(File toDir, File backupDir) throws IOException {
    if (myInPlace) {
      Utils.copy(getFile(toDir), getFile(backupDir), false);
    }
    else {
      File moveBackup = getSource(backupDir);
      if (!moveBackup.exists()) {
        Utils.copy(getSource(toDir), moveBackup, false);
      }
    }
  }

  protected void replaceUpdated(File from, File dest) throws IOException {
    if (Utils.IS_MAC) {
      // on macOS, code signing caches seem to be associated with specific file IDs, so we need to remove the original file
      Utils.delete(dest);
      Utils.copy(from, dest, false);
    }
    else {
      Utils.copy(from, dest, true);
    }
  }

  @Override
  protected void doRevert(File toFile, File backupFile) throws IOException {
    if (myInPlace) {
      if (!Files.exists(toFile.toPath()) || isModified(toFile)) {
        Utils.copy(backupFile, toFile, true);
      }
    }
    else {
      Utils.delete(toFile);
    }
  }

  protected void writeDiff(InputStream olderFileIn, InputStream newerFileIn, OutputStream patchOutput) throws IOException {
    if (isCritical()) {
      LOG.info("critical: " + mySource);

      patchOutput.write(RAW);
      Utils.copyStream(newerFileIn, patchOutput);

      return;
    }

    LOG.info(mySource);
    ByteArrayOutputStream diffOutput = new OpenByteArrayOutputStream();
    byte[] newerFileBuffer = JBDiff.bsdiff(olderFileIn, newerFileIn, diffOutput, myPatch.getTimeout());
    diffOutput.close();

    int diffSize = diffOutput.size();
    if (0 < diffSize && diffSize < newerFileBuffer.length) {
      patchOutput.write(COMPRESSED);
      diffOutput.writeTo(patchOutput);
    }
    else {
      patchOutput.write(RAW);
      Utils.writeBytes(newerFileBuffer, newerFileBuffer.length, patchOutput);
      if (diffSize == 0 && newerFileBuffer.length != 0) {
        LOG.warning("*** 'bsdiff' timed out, dumping the file as-is");
      }
    }
  }

  protected void applyDiff(InputStream patchInput, InputStream oldFileIn, OutputStream toFileOut) throws IOException {
    int type = patchInput.read();
    if (type == COMPRESSED) {
      JBPatch.bspatch(oldFileIn, toFileOut, patchInput);
    }
    else if (type == RAW) {
      Utils.copyStream(patchInput, toFileOut);
    }
    else {
      throw new IOException("Corrupted patch");
    }
  }

  @Override
  public String toString() {
    String text = super.toString();
    if (!myInPlace) {
      text = text.substring(0, text.length() - 1) + ", " + (myIsMove ? '=' : '~') + mySource + ')';
    }
    return text;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;

    BaseUpdateAction that = (BaseUpdateAction)o;

    if (myIsMove != that.myIsMove) return false;
    if (!Objects.equals(mySource, that.mySource)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myIsMove ? 1 : 0);
    result = 31 * result + Objects.hashCode(mySource);
    return result;
  }
}
