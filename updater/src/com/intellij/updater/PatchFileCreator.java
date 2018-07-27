// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class PatchFileCreator {
  private static final String PATCH_INFO_FILE_NAME = ".patch-info";

  public static Patch create(PatchSpec spec, File patchFile, UpdaterUI ui) throws IOException {
    Runner.logger().info("Creating the patch file '" + patchFile + "'...");
    ui.startProcess("Creating the patch file '" + patchFile + "'...");

    Patch patchInfo = new Patch(spec, ui);

    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(patchFile))) {
      out.setLevel(9);

      out.putNextEntry(new ZipEntry(PATCH_INFO_FILE_NAME));
      patchInfo.write(out);
      out.closeEntry();

      File olderDir = new File(spec.getOldFolder());
      File newerDir = new File(spec.getNewFolder());
      List<PatchAction> actions = patchInfo.getActions();
      for (PatchAction each : actions) {
        Runner.logger().info("Packing " + each.getPath());
        each.buildPatchFile(olderDir, newerDir, out);
      }
    }

    return patchInfo;
  }

  public static PreparationResult prepareAndValidate(File patchFile,
                                                     File toDir,
                                                     UpdaterUI ui) throws IOException, OperationCancelledException {
    Patch patch;

    try (ZipFile zipFile = new ZipFile(patchFile);
         InputStream in = Utils.getEntryInputStream(zipFile, PATCH_INFO_FILE_NAME)) {
      patch = new Patch(in);
    }

    Runner.logger().info(patch.getOldBuild() + " -> " + patch.getNewBuild());
    ui.setDescription(patch.getOldBuild(), patch.getNewBuild());

    List<ValidationResult> validationResults = patch.validate(toDir, ui);
    return new PreparationResult(patch, patchFile, toDir, validationResults);
  }

  public static ApplicationResult apply(PreparationResult preparationResult,
                                        Map<String, ValidationResult.Option> options,
                                        File backupDir,
                                        UpdaterUI ui) throws IOException {
    try (ZipFile zipFile = new ZipFile(preparationResult.patchFile)) {
      return preparationResult.patch.apply(zipFile, preparationResult.toDir, backupDir, options, ui);
    }
  }

  public static void revert(PreparationResult preparationResult,
                            List<PatchAction> actionsToRevert,
                            File backupDir,
                            UpdaterUI ui) throws IOException {
    preparationResult.patch.revert(actionsToRevert, backupDir, preparationResult.toDir, ui);
  }

  public static class PreparationResult {
    public final Patch patch;
    public final File patchFile;
    public final File toDir;
    public final List<ValidationResult> validationResults;

    public PreparationResult(Patch patch, File patchFile, File toDir, List<ValidationResult> validationResults) {
      this.patch = patch;
      this.patchFile = patchFile;
      this.toDir = toDir;
      this.validationResults = validationResults;
    }
  }

  public static class ApplicationResult {
    public final boolean applied;
    public final List<PatchAction> appliedActions;
    public final Throwable error;

    public ApplicationResult(boolean applied, List<PatchAction> appliedActions) {
      this(applied, appliedActions, null);
    }

    public ApplicationResult(boolean applied, List<PatchAction> appliedActions, Throwable error) {
      this.applied = applied;
      this.appliedActions = appliedActions;
      this.error = error;
    }
  }
}