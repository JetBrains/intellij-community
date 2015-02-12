package com.intellij.updater;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class DeleteAction extends PatchAction {
  public DeleteAction(Patch patch, String path, long checksum) {
    super(patch, path, checksum);
  }

  public DeleteAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
  }

  @Override
  public void doBuildPatchFile(File olderDir, File newerFile, ZipOutputStream patchOutput) throws IOException {
    // do nothing
  }

  @Override
  public ValidationResult validate(File toDir) throws IOException {
    File toFile = getFile(toDir);
    ValidationResult result = doValidateAccess(toFile, ValidationResult.Action.DELETE);
    if (result != null) return result;

    if (myPatch.validateDeletion(myPath) && toFile.exists() && isModified(toFile)) {
      ValidationResult.Option[] options = myPatch.isStrict()
                                          ? new ValidationResult.Option[]{ValidationResult.Option.DELETE}
                                          : new ValidationResult.Option[]{ValidationResult.Option.DELETE, ValidationResult.Option.KEEP};
      ValidationResult.Action action = myChecksum == Digester.INVALID ? ValidationResult.Action.VALIDATE : ValidationResult.Action.DELETE;
      String message = myChecksum == Digester.INVALID ? "Unexpected file" : "Modified";
      return new ValidationResult(ValidationResult.Kind.CONFLICT, myPath, action, message, options);
    }
    return null;
  }

  @Override
  protected boolean doShouldApply(File toDir) {
    return getFile(toDir).exists();
  }

  @Override
  protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
    Utils.delete(toFile);
  }

  @Override
  protected void doBackup(File toFile, File backupFile) throws IOException {
    Utils.copy(toFile, backupFile);
  }

  @Override
  protected void doRevert(File toFile, File backupFile) throws IOException {
    if (!toFile.exists() || toFile.isDirectory() || isModified(toFile)) {
      Utils.delete(toFile); // make sure there is no directory remained on this path (may remain from previous 'create' actions
      Utils.copy(backupFile, toFile);
    }
  }
}
