// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static com.intellij.updater.Runner.LOG;

public final class PatchFileCreator {
  private static final String PATCH_INFO_FILE_NAME = ".patch-info";

  public static Patch create(PatchSpec spec, File patchFile, UpdaterUI ui, Path cacheDir) throws IOException {
    LOG.info("Creating the patch file '" + patchFile + "'...");
    ui.startProcess("Creating the patch file '" + patchFile + "'...");

    Patch patchInfo = new Patch(spec, ui);

    LOG.info("Packing entries...");
    ui.startProcess("Packing entries...");

    List<PatchAction> actions = patchInfo.getActions();
    File olderDir = new File(spec.getOldFolder());
    File newerDir = new File(spec.getNewFolder());
    ExecutorService executor = Executors.newFixedThreadPool(Math.max(Runtime.getRuntime().availableProcessors() - 1, 1));
    Map<PatchAction, Future<Path>> tasks = new ConcurrentHashMap<>();

    for (int i = 0; i < actions.size(); i++) {
      PatchAction action = actions.get(i);
      if (action instanceof UpdateAction && !action.isCritical()) {
        int _i = i;
        tasks.put(action, executor.submit(() -> {
          Path cached = null;
          if (cacheDir != null) {
            String cachedName = getCachedFileName((UpdateAction)action, newerDir);
            if (cachedName != null) {
              cached = cacheDir.resolve(cachedName);
              if (Files.exists(cached) && Files.isRegularFile(cached)) {
                LOG.info("Reusing diff for " + action.getPath() + " : " + cachedName);
                return cached;
              }
            }
          }
          Path temp = Utils.getTempFile("diff_" + _i).toPath();
          try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
            out.setLevel(0);
            action.buildPatchFile(olderDir, newerDir, out);
          }
          if (cached != null) {
            Files.createDirectories(cached.getParent());
            Files.copy(temp, cached);
            LOG.info("Caching diff for " + action.getPath() + " : " + cached.getFileName());
          }
          return temp;
        }));
      }
    }

    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(patchFile))) {
      out.setLevel(9);

      LOG.info("Packing " + PATCH_INFO_FILE_NAME);
      out.putNextEntry(new ZipEntry(PATCH_INFO_FILE_NAME));
      patchInfo.write(out);
      out.closeEntry();

      for (PatchAction action : actions) {
        LOG.info("Packing " + action.getPath());
        Future<Path> task = tasks.get(action);
        if (task == null) {
          action.buildPatchFile(olderDir, newerDir, out);
        }
        else {
          try {
            Path temp = task.get();
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(temp))) {
              ZipEntry entry;
              while ((entry = in.getNextEntry()) != null) {
                out.putNextEntry(new ZipEntry(entry.getName()));
                Utils.copyStream(in, out);
                out.closeEntry();
              }
            }
          }
          catch (InterruptedException e) { throw new IOException(e); }
          catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException)cause;
            if (cause instanceof RuntimeException) throw (RuntimeException)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new RuntimeException(e);
          }
        }
      }
    }

    executor.shutdown();

    return patchInfo;
  }

  private static String getCachedFileName(UpdateAction action, File newerDir) {
    if (action.isMove()) return null;
    if (!Digester.isFile(action.getChecksum())) return null;
    // Single file diff is a zip file with single entry corresponding to target file name, so entry name should be part of caching key
    // Also both source and target digests are part of key
    try {
      return "diff-v1-" +
             action.getPath().replaceAll("[^A-Za-z0-9_\\-]","_") +
             '-' +
             Long.toHexString(action.getChecksum()) +
             '-' +
             Long.toHexString(Digester.digestRegularFile(action.getFile(newerDir)));
    }
    catch (IOException ignored) {
      return null;
    }
  }

  public static PreparationResult prepareAndValidate(File patchFile,
                                                     File toDir,
                                                     UpdaterUI ui) throws IOException, OperationCancelledException {
    Patch patch;

    try (ZipFile zipFile = new ZipFile(patchFile);
         InputStream in = Utils.getEntryInputStream(zipFile, PATCH_INFO_FILE_NAME)) {
      patch = new Patch(in);
    }

    LOG.info(patch.getOldBuild() + " -> " + patch.getNewBuild());
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
