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
import java.util.*;
import java.util.zip.ZipFile;

public class Patch {
  private static final int CREATE_ACTION_KEY = 1;
  private static final int UPDATE_ACTION_KEY = 2;
  private static final int UPDATE_ZIP_ACTION_KEY = 3;
  private static final int DELETE_ACTION_KEY = 4;
  private static final int VALIDATE_ACTION_KEY = 5;

  private final String myOldBuild;
  private final String myNewBuild;
  private final String myRoot;
  private final boolean myIsBinary;
  private final boolean myIsStrict;
  private final boolean myIsNormalized;
  private final Map<String, String> myWarnings;
  private final List<String> myDeleteFiles;
  private final List<PatchAction> myActions;

  public Patch(PatchSpec spec, UpdaterUI ui) throws IOException, OperationCancelledException {
    myOldBuild = spec.getOldVersionDescription();
    myNewBuild = spec.getNewVersionDescription();
    myRoot = spec.getRoot();
    myIsBinary = spec.isBinary();
    myIsStrict = spec.isStrict();
    myIsNormalized = spec.isNormalized();
    myWarnings = spec.getWarnings();
    myDeleteFiles = spec.getDeleteFiles();
    myActions = calculateActions(spec, ui);
  }

  public Patch(InputStream patchIn) throws IOException {
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataInputStream in = new DataInputStream(patchIn);
    myOldBuild = in.readUTF();
    myNewBuild = in.readUTF();
    myRoot = in.readUTF();
    myIsBinary = in.readBoolean();
    myIsStrict = in.readBoolean();
    myIsNormalized = in.readBoolean();
    myWarnings = readMap(in);
    myDeleteFiles = readList(in);
    myActions = readActions(in);
  }

  private List<PatchAction> calculateActions(PatchSpec spec, UpdaterUI ui) throws IOException, OperationCancelledException {
    Runner.logger().info("Calculating difference...");
    ui.startProcess("Calculating difference...");
    ui.checkCancelled();

    File olderDir = new File(spec.getOldFolder());
    File newerDir = new File(spec.getNewFolder());
    Map<String, Long> oldChecksums = digestFiles(olderDir, spec.getIgnoredFiles(), isNormalized(), ui);
    Map<String, Long> newChecksums = digestFiles(newerDir, spec.getIgnoredFiles(), false, ui);
    DiffCalculator.Result diff = DiffCalculator.calculate(oldChecksums, newChecksums, spec.getCriticalFiles(), spec.getOptionalFiles(), true);

    List<PatchAction> tempActions = new ArrayList<>();

    // 'delete' actions before 'create' actions to prevent newly created files to be deleted if the names differ only on case.
    for (Map.Entry<String, Long> each : diff.filesToDelete.entrySet()) {
      // Add them in reverse order so directory structures start deleting the files before the directory itself.
      tempActions.add(0, new DeleteAction(this, each.getKey(), each.getValue()));
    }

    for (String each : diff.filesToCreate.keySet()) {
      tempActions.add(new CreateAction(this, each));
    }

    for (Map.Entry<String, DiffCalculator.Update> each : diff.filesToUpdate.entrySet()) {
      DiffCalculator.Update update = each.getValue();
      if (!spec.isBinary() && !update.move && Utils.isZipFile(each.getKey())) {
        tempActions.add(new UpdateZipAction(this, each.getKey(), update.source, update.checksum));
      }
      else {
        tempActions.add(new UpdateAction(this, each.getKey(), update.source, update.checksum, update.move));
      }
    }

    if (spec.isStrict()) {
      for (Map.Entry<String, Long> each : diff.commonFiles.entrySet()) {
        tempActions.add(new ValidateAction(this, each.getKey(), each.getValue()));
      }
    }

    Runner.logger().info("Preparing actions...");
    ui.startProcess("Preparing actions...");
    ui.checkCancelled();

    List<PatchAction> actions = new ArrayList<>();
    for (PatchAction action : tempActions) {
      ui.setStatus(action.getPath());
      ui.checkCancelled();

      if (!action.calculate(olderDir, newerDir)) continue;
      actions.add(action);
      action.setCritical(spec.getCriticalFiles().contains(action.getPath()));
      action.setOptional(spec.getOptionalFiles().contains(action.getPath()));
    }
    return actions;
  }

  public List<PatchAction> getActions() {
    return myActions;
  }

  public void write(OutputStream out) throws IOException {
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataOutputStream dataOut = new DataOutputStream(out);
    try {
      dataOut.writeUTF(myOldBuild);
      dataOut.writeUTF(myNewBuild);
      dataOut.writeUTF(myRoot);
      dataOut.writeBoolean(myIsBinary);
      dataOut.writeBoolean(myIsStrict);
      dataOut.writeBoolean(myIsNormalized);
      writeMap(dataOut, myWarnings);
      writeList(dataOut, myDeleteFiles);
      writeActions(dataOut, myActions);
    }
    finally {
      dataOut.flush();
    }
  }

  private static void writeList(DataOutputStream dataOut, List<String> list) throws  IOException {
    dataOut.writeInt(list.size());
    for (String string : list) {
      dataOut.writeUTF(string);
    }
  }

  private static void writeMap(DataOutputStream dataOut, Map<String, String> map) throws IOException {
    dataOut.writeInt(map.size());
    for (Map.Entry<String, String> entry : map.entrySet()) {
      dataOut.writeUTF(entry.getKey());
      dataOut.writeUTF(entry.getValue());
    }
  }

  private static void writeActions(DataOutputStream dataOut, List<PatchAction> actions) throws IOException {
    dataOut.writeInt(actions.size());

    for (PatchAction each : actions) {
      int key;
      Class clazz = each.getClass();

      if (clazz == CreateAction.class) {
        key = CREATE_ACTION_KEY;
      }
      else if (clazz == UpdateAction.class) {
        key = UPDATE_ACTION_KEY;
      }
      else if (clazz == UpdateZipAction.class) {
        key = UPDATE_ZIP_ACTION_KEY;
      }
      else if (clazz == DeleteAction.class) {
        key = DELETE_ACTION_KEY;
      }
      else if (clazz == ValidateAction.class) {
        key = VALIDATE_ACTION_KEY;
      }
      else {
        throw new RuntimeException("Unknown action " + each);
      }
      dataOut.writeInt(key);
      each.write(dataOut);
    }
  }

  private static List<String> readList(DataInputStream in) throws IOException {
    int size = in.readInt();
    List<String> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      list.add(in.readUTF());
    }
    return list;
  }

  private static Map<String, String> readMap(DataInputStream in) throws IOException {
    int size = in.readInt();
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < size; i++) {
      String key = in.readUTF();
      map.put(key, in.readUTF());
    }
    return map;
  }

  private List<PatchAction> readActions(DataInputStream in) throws IOException {
    List<PatchAction> actions = new ArrayList<>();
    int size = in.readInt();
    while (size-- > 0) {
      int key = in.readInt();
      PatchAction a;
      switch (key) {
        case CREATE_ACTION_KEY:
          a = new CreateAction(this, in);
          break;
        case UPDATE_ACTION_KEY:
          a = new UpdateAction(this, in);
          break;
        case UPDATE_ZIP_ACTION_KEY:
          a = new UpdateZipAction(this, in);
          break;
        case DELETE_ACTION_KEY:
          a = new DeleteAction(this, in);
          break;
        case VALIDATE_ACTION_KEY:
          a = new ValidateAction(this, in);
          break;
        default:
          throw new RuntimeException("Unknown action type " + key);
      }
      actions.add(a);
    }
    return actions;
  }

  private File toBaseDir(File toDir) throws IOException {
    // This removes myRoot from the end of toDir. myRoot is expressed with '/' so converting to URI to normalize separators.
    String path = toDir.toURI().getPath();
    if (!path.endsWith(myRoot)) {
      throw new IOException("The patch must be applied to the root folder " + myRoot);
    }
    return new File(path.substring(0, path.length() - myRoot.length()));
  }

  public List<ValidationResult> validate(File rootDir, UpdaterUI ui) throws IOException, OperationCancelledException {
    LinkedHashSet<String> files = null;
    File toDir = toBaseDir(rootDir);
    boolean checkWarnings = true;
    while (checkWarnings) {
      //always collect files and folders to avoid cases such as IDEA-152249
      files = Utils.collectRelativePaths(toDir);
      checkWarnings = false;
      for (String file : files) {
        String warning = myWarnings.get(file);
        if (warning != null) {
          if (!ui.showWarning(warning)) {
            throw new OperationCancelledException();
          }
          checkWarnings = true;
          break;
        }
      }
    }

    if (myIsStrict) {
      // In strict mode add delete actions for unknown files.
      for (PatchAction action : myActions) {
        files.remove(action.getPath());
      }
      for (String file : files) {
        myActions.add(0, new DeleteAction(this, file, Digester.INVALID));
      }
    }

    List<ValidationResult> results = new ArrayList<>();

    Set<String> deletedPaths = new HashSet<>();
    Runner.logger().info("Validating installation...");
    forEach(myActions, "Validating installation...", ui, true, action -> {
      ValidationResult result = action.validate(toDir);

      if (action instanceof DeleteAction) {
        deletedPaths.add(mapPath(action.getPath()));
      }
      else if (action instanceof CreateAction &&
               result != null &&
               ValidationResult.ALREADY_EXISTS_MESSAGE.equals(result.message) &&
               deletedPaths.contains(mapPath(action.getPath()))) {
        // do not warn about files which are going to be deleted
        result = null;
      }

      if (result != null) results.add(result);
    });

    return results;
  }

  private static String mapPath(String path) {
    return Runner.isCaseSensitiveFs() ? path : path.toLowerCase(Locale.getDefault());
  }

  public ApplicationResult apply(ZipFile patchFile,
                                 File rootDir,
                                 File backupDir,
                                 Map<String, ValidationResult.Option> options,
                                 UpdaterUI ui) throws IOException, OperationCancelledException {
    File toDir = toBaseDir(rootDir);
    List<PatchAction> actionsToProcess = new ArrayList<>();
    for (PatchAction each : myActions) {
      if (each.shouldApply(toDir, options)) actionsToProcess.add(each);
    }

    if (backupDir != null) {
      forEach(actionsToProcess, "Backing up files...", ui, true, action -> action.backup(toDir, backupDir));
    }

    List<PatchAction> appliedActions = new ArrayList<>();
    List<File> createdDirectories = new ArrayList<>();
    Set<File> createdOptionalFiles = new HashSet<>();
    boolean shouldRevert = false;
    boolean cancelled = false;

    try {
      forEach(actionsToProcess, "Applying patch...", ui, true, action -> {
        if (action instanceof CreateAction && !new File(toDir, action.getPath()).getParentFile().exists()) {
          Runner.logger().info("Create action: " + action.getPath() + " skipped. The parent folder is absent.");
        }
        else if (action instanceof UpdateAction && !new File(toDir, action.getPath()).getParentFile().exists()) {
          Runner.logger().info("Update action: " + action.getPath() + " skipped. The parent folder is absent.");
        }
        else {
          appliedActions.add(action);
          action.apply(patchFile, backupDir, toDir);

          if (action instanceof CreateAction) {
            File file = action.getFile(toDir);
            if (file.isDirectory()) {
              createdDirectories.add(0, file);
            }
            else if (action.isOptional()) {
              createdOptionalFiles.add(file);
            }
          }
        }
      });
    }
    catch (OperationCancelledException e) {
      Runner.printStackTrace(e);
      shouldRevert = true;
      cancelled = true;
    }
    catch (Throwable e) {
      Runner.printStackTrace(e);
      shouldRevert = true;
      ui.showError(e);
    }

    if (shouldRevert) {
      if (backupDir != null) {
        revert(appliedActions, backupDir, rootDir, ui);
      }
      appliedActions.clear();

      if (cancelled) throw new OperationCancelledException();
    }
    else {
      for (File directory : createdDirectories) {
        File[] children = directory.listFiles();
        if (children != null && createdOptionalFiles.containsAll(Arrays.asList(children))) {
          Runner.logger().info("Pruning empty directory: " + directory);
          Utils.delete(directory);
        }
      }
    }

    // on macOS, we need to update bundle timestamp to reset Info.plist caches
    //noinspection ResultOfMethodCallIgnored
    toDir.setLastModified(System.currentTimeMillis());

    return new ApplicationResult(appliedActions);
  }

  public void revert(List<PatchAction> actions, File backupDir, File rootDir, UpdaterUI ui) throws OperationCancelledException, IOException {
    List<PatchAction> reverse = new ArrayList<>(actions);
    Collections.reverse(reverse);
    File toDir = toBaseDir(rootDir);
    forEach(reverse, "Reverting...", ui, false, action -> action.revert(toDir, backupDir));
  }

  private static void forEach(List<PatchAction> actions,
                              String title,
                              UpdaterUI ui,
                              boolean canBeCancelled,
                              ActionsProcessor processor) throws OperationCancelledException, IOException {
    ui.startProcess(title);
    if (canBeCancelled) ui.checkCancelled();

    for (int i = 0; i < actions.size(); i++) {
      PatchAction each = actions.get(i);

      ui.setStatus(each.getPath());
      if (canBeCancelled) ui.checkCancelled();

      processor.forEach(each);

      ui.setProgress((i + 1) * 100 / actions.size());
    }
  }

  public long digestFile(File toFile, boolean normalize) throws IOException {
    if (!myIsBinary && Utils.isZipFile(toFile.getName())) {
      return Digester.digestZipFile(toFile);
    }
    else {
      return Digester.digestRegularFile(toFile, normalize);
    }
  }

  public Map<String, Long> digestFiles(File dir, List<String> ignoredFiles, boolean normalize, UpdaterUI ui) throws IOException, OperationCancelledException {
    Map<String, Long> result = new LinkedHashMap<>();
    //always collect files and folders to avoid cases such as IDEA-152249
    LinkedHashSet<String> paths = Utils.collectRelativePaths(dir);
    for (String each : paths) {
      if (ignoredFiles.contains(each)) continue;
      ui.setStatus(each);
      ui.checkCancelled();
      result.put(each, digestFile(new File(dir, each), normalize));
    }
    return result;
  }

  public String getOldBuild() {
    return myOldBuild;
  }

  public String getNewBuild() {
    return myNewBuild;
  }

  public boolean isStrict() {
    return myIsStrict;
  }

  public boolean isNormalized() {
    return myIsNormalized;
  }

  public boolean validateDeletion(String path) {
    for (String delete : myDeleteFiles) {
      if (path.matches(delete)) {
        return false;
      }
    }
    return true;
  }

  @FunctionalInterface
  private interface ActionsProcessor {
    void forEach(PatchAction action) throws IOException;
  }

  public static class ApplicationResult {
    public final boolean applied;
    public final List<PatchAction> appliedActions;

    public ApplicationResult(List<PatchAction> appliedActions) {
      this.applied = !appliedActions.isEmpty();
      this.appliedActions = appliedActions;
    }
  }
}