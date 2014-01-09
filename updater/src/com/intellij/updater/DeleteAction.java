package com.intellij.updater;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class DeleteAction extends PatchAction {
  public DeleteAction(String path, long checksum) {
    super(path, checksum);
  }

  public DeleteAction(DataInputStream in) throws IOException {
    super(in);
  }

  @Override
  public void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
    // do nothing
  }

  @Override
  protected ValidationResult doValidate(File toFile) throws IOException {
    ValidationResult result = doValidateAccess(toFile, ValidationResult.Action.DELETE);
    if (result != null) return result;
    
    if (toFile.exists() && isModified(toFile)) {
      return new ValidationResult(ValidationResult.Kind.CONFLICT,
                                  myPath,
                                  ValidationResult.Action.DELETE, 
                                  "Modified",
                                  ValidationResult.Option.DELETE,
                                  ValidationResult.Option.KEEP);
    }
    return null;
  }

  @Override
  protected boolean shouldApplyOn(File toFile) {
    return toFile.exists();
  }

  @Override
  protected void doApply(ZipFile patchFile, File toFile) throws IOException {
    Utils.delete(toFile);
    Runner.logger.trace("DeleteAction.doApply delete File: " + toFile.getCanonicalPath());
  }

  protected void doBackup(File toFile, File backupFile) throws IOException {
    Utils.copy(toFile, backupFile);
    Runner.logger.trace("DeleteAction.doBackup copy toFile: " + toFile.getCanonicalPath() + " backupFile " + backupFile.getCanonicalPath());
  }

  protected void doRevert(File toFile, File backupFile) throws IOException {
    Utils.delete(toFile); // make sure there is no directory remained on this path (may remain from previous 'create' actions
    Runner.logger.trace("DeleteAction.doRevert delete toFile: " + toFile.getCanonicalPath());
    Utils.copy(backupFile, toFile);
    Runner.logger.trace("DeleteAction.doRevert copy backupFile: " + backupFile.getCanonicalPath() + " toFile " + toFile.getCanonicalPath());
  }
}
