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