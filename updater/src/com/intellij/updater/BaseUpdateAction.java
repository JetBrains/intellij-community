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
import java.util.Objects;
import java.util.zip.ZipOutputStream;

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

  public String getSourcePath() {
    return mySource;
  }

  public boolean isMove() {
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
    return doValidateNotChanged(fromFile, ValidationResult.Kind.ERROR, ValidationResult.Action.UPDATE);
  }

  @Override
  protected void doBackup(File toFile, File backupFile) throws IOException {
    if (myInPlace) {
      Utils.copy(toFile, backupFile);
    }
  }

  protected void replaceUpdated(File from, File dest) throws IOException {
    // on macOS, code signing caches seem to be associated with specific file ids, so we need to remove the original file
    Utils.delete(dest);
    Utils.copy(from, dest);
  }

  @Override
  protected void doRevert(File toFile, File backupFile) throws IOException {
    if (myInPlace) {
      if (!toFile.exists() || isModified(toFile)) {
        Utils.delete(toFile);
        Utils.copy(backupFile, toFile);
      }
    }
    else {
      Utils.delete(toFile);
    }
  }

  protected void writeDiff(File olderFile, File newerFile, OutputStream patchOutput) throws IOException {
    DiffAlgorithm algorithm = DiffAlgorithm.determineDiffAlgorithm(olderFile, newerFile, isCritical(), myPatch.getLargeFileCutoff());
    patchOutput.write(algorithm.getId());
    algorithm.writeDiff(olderFile, newerFile, patchOutput);
  }

  protected void writeDiff(InputStream olderFileIn, InputStream newerFileIn, OutputStream patchOutput) throws IOException {
    DiffAlgorithm algorithm = DiffAlgorithm.determineDiffAlgorithm(null, null, isCritical(), myPatch.getLargeFileCutoff());
    patchOutput.write(algorithm.getId());
    algorithm.writeDiff(olderFileIn, newerFileIn, patchOutput);
  }

  private static DiffAlgorithm readDiffAlgorithm(InputStream patchInput) throws IOException {
    int type = patchInput.read();
    return DiffAlgorithm.getAlgorithmForId(type);
  }

  protected void applyDiff(InputStream patchInput, InputStream oldFileIn, OutputStream toFileOut) throws IOException {
    DiffAlgorithm algorithm = readDiffAlgorithm(patchInput);
    algorithm.applyDiff(patchInput, oldFileIn, toFileOut);
  }

  protected void applyDiff(InputStream patchInput, File oldFileIn, OutputStream toFileOut) throws IOException {
    DiffAlgorithm algorithm = readDiffAlgorithm(patchInput);
    algorithm.applyDiff(patchInput, oldFileIn, toFileOut);
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