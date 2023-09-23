// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static com.intellij.updater.Runner.LOG;

public class DeleteAction extends PatchAction {
  public DeleteAction(Patch patch, String path, long checksum) {
    super(patch, path, checksum);
  }

  public DeleteAction(Patch patch, DataInputStream in) throws IOException {
    super(patch, in);
  }

  @Override
  public void doBuildPatchFile(File olderDir, File newerFile, ZipOutputStream patchOutput) {
    // do nothing
  }

  @Override
  public ValidationResult validate(File toDir) throws IOException {
    File toFile = getFile(toDir);
    ValidationResult result = doValidateAccess(toFile, ValidationResult.Action.DELETE, false);
    if (result != null) return result;

    if (myPatch.validateDeletion(getPath()) && toFile.exists() && isModified(toFile)) {
      ValidationResult.Option[] options = myPatch.isStrict()
                                          ? new ValidationResult.Option[]{ValidationResult.Option.DELETE}
                                          : new ValidationResult.Option[]{ValidationResult.Option.DELETE, ValidationResult.Option.KEEP};
      if (getChecksum() == Digester.INVALID) {
        ValidationResult.Action action = ValidationResult.Action.VALIDATE;
        String details = "checksum 0x" + Long.toHexString(myPatch.digestFile(toFile));
        return new ValidationResult(ValidationResult.Kind.CONFLICT, getPath(), action, "Unexpected file", details, options);
      }
      else {
        ValidationResult.Action action = ValidationResult.Action.DELETE;
        String details = "expected 0x" + Long.toHexString(getChecksum()) + ", actual 0x" + Long.toHexString(myPatch.digestFile(toFile));
        return new ValidationResult(ValidationResult.Kind.CONFLICT, getPath(), action, ValidationResult.MODIFIED_MESSAGE, details, options);
      }
    }

    return null;
  }

  @Override
  protected boolean doShouldApply(File toDir) {
    return getFile(toDir).exists();
  }

  @Override
  protected void doBackup(File toFile, File backupFile) throws IOException {
    Utils.copy(toFile, backupFile, false);
  }

  @Override
  protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
    LOG.info("Delete action. File: " + toFile.getAbsolutePath());

    // a directory can be deleted only when it does not contain any user's content
    boolean canDelete = true;
    if (Files.isDirectory(toFile.toPath(), LinkOption.NOFOLLOW_LINKS)) {
      try (Stream<Path> children = Files.list(toFile.toPath())) {
        canDelete = children.findAny().isEmpty();
      }
    }

    if (canDelete) {
      LOG.info("Delete: " + toFile.getAbsolutePath());
      Utils.delete(toFile);
    }
    else {
      LOG.info("Preserved: " + toFile.getAbsolutePath());
    }
  }

  @Override
  protected void doRevert(File toFile, File backupFile) throws IOException {
    if (!Files.exists(toFile.toPath()) || Files.isDirectory(toFile.toPath(), LinkOption.NOFOLLOW_LINKS) || isModified(toFile)) {
      Utils.copy(backupFile, toFile, true);
    }
  }
}
