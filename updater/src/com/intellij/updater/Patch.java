// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static com.intellij.updater.Runner.*;

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
  private final int myTimeout;
  private final List<PatchAction> myActions;

  public Patch(PatchSpec spec, UpdaterUI ui) throws IOException {
    myOldBuild = spec.getOldVersionDescription();
    myNewBuild = spec.getNewVersionDescription();
    myRoot = spec.getRoot();
    myIsBinary = spec.isBinary();
    myIsStrict = spec.isStrict();
    myIsNormalized = spec.isNormalized();
    myWarnings = spec.getWarnings();
    myDeleteFiles = spec.getDeleteFiles();
    myTimeout = spec.getTimeout();
    myActions = calculateActions(spec, ui);
  }

  public Patch(InputStream patchIn) throws IOException {
    DataInputStream in = new DataInputStream(patchIn);
    myOldBuild = in.readUTF();
    myNewBuild = in.readUTF();
    myRoot = in.readUTF();
    myIsBinary = in.readBoolean();
    myIsStrict = in.readBoolean();
    myIsNormalized = in.readBoolean();
    myWarnings = readMap(in);
    myDeleteFiles = readList(in);
    myTimeout = 0;
    myActions = readActions(in);
  }

  private List<PatchAction> calculateActions(PatchSpec spec, UpdaterUI ui) throws IOException {
    LOG.info("Calculating difference...");
    ui.startProcess("Calculating difference...");

    File olderDir = new File(spec.getOldFolder());
    File newerDir = new File(spec.getNewFolder());

    Set<String> ignored = new HashSet<>(spec.getIgnoredFiles());
    Set<String> critical = new HashSet<>(spec.getCriticalFiles());
    Set<String> optional = new HashSet<>(spec.getOptionalFiles());
    Set<String> strict = new HashSet<>(spec.getStrictFiles());

    Map<String, Long> oldChecksums = digestFiles(olderDir, ignored, isNormalized());
    Map<String, Long> newChecksums = digestFiles(newerDir, ignored, false);
    DiffCalculator.Result diff = DiffCalculator.calculate(oldChecksums, newChecksums, critical, optional, true);

    LOG.info("Preparing actions...");
    ui.startProcess("Preparing actions...");

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

    List<PatchAction> actions = new ArrayList<>();
    for (PatchAction action : tempActions) {
      LOG.info(action.getPath());
      if (action.calculate(olderDir, newerDir)) {
        actions.add(action);
        action.setCritical(critical.contains(action.getPath()));
        action.setOptional(optional.contains(action.getPath()));
        action.setStrict(strict.contains(action.getPath()));
      }
    }
    return actions;
  }

  public List<PatchAction> getActions() {
    return myActions;
  }

  public void write(OutputStream out) throws IOException {
    DataOutputStream dataOut = new DataOutputStream(out);
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

  private static void writeActions(DataOutputStream dataOut, List<? extends PatchAction> actions) throws IOException {
    dataOut.writeInt(actions.size());
    for (PatchAction each : actions) {
      int key;
      Class<?> clazz = each.getClass();
      if (clazz == CreateAction.class) key = CREATE_ACTION_KEY;
      else if (clazz == UpdateAction.class) key = UPDATE_ACTION_KEY;
      else if (clazz == UpdateZipAction.class) key = UPDATE_ZIP_ACTION_KEY;
      else if (clazz == DeleteAction.class) key = DELETE_ACTION_KEY;
      else if (clazz == ValidateAction.class) key = VALIDATE_ACTION_KEY;
      else throw new RuntimeException("Unknown action " + each);
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
      files = Utils.collectRelativePaths(toDir.toPath());
      checkWarnings = false;
      for (String file : files) {
        String warning = myWarnings.get(file);
        if (warning != null) {
          ui.askUser(warning);
          checkWarnings = true;
          break;
        }
      }
    }

    if (myIsStrict) {
      // in the strict mode, add delete actions for unknown files
      for (PatchAction action : myActions) {
        files.remove(action.getPath());
      }
      for (String file : files) {
        myActions.add(0, new DeleteAction(this, file, Digester.INVALID));
      }
    }

    List<ValidationResult> results = new ArrayList<>();

    Set<String> deletedPaths = new HashSet<>(), deletedLinks = new HashSet<>();
    forEach(myActions, "Validating installation...", ui, action -> {
      ValidationResult result = action.validate(toDir);

      if (action instanceof DeleteAction) {
        String path = mapPath(action.getPath());
        deletedPaths.add(path);
        if (Digester.isSymlink(action.getChecksum())) {
          deletedLinks.add(path);
        }
      }
      else if (result != null &&
               action instanceof CreateAction &&
               ValidationResult.ALREADY_EXISTS_MESSAGE.equals(result.message) &&
               toBeDeleted(mapPath(action.getPath()), deletedPaths, deletedLinks)) {
        result = null;  // do not warn about files going to be deleted
      }

      if (result != null) results.add(result);
    });

    return results;
  }

  private static String mapPath(String path) {
    if (!isCaseSensitiveFs()) {
      path = path.toLowerCase(Locale.getDefault());
    }
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }
    return path;
  }

  private static boolean toBeDeleted(String path, Set<String> deletedPaths, Set<String> deletedLinks) {
    if (deletedPaths.contains(path)) return true;
    if (!deletedLinks.isEmpty()) {
      int p = path.length();
      while ((p = path.lastIndexOf('/', p - 1)) > 0) {
        if (deletedLinks.contains(path.substring(0, p))) return true;
      }
    }
    return false;
  }

  public PatchFileCreator.ApplicationResult apply(ZipFile patchFile,
                                                  File rootDir,
                                                  File backupDir,
                                                  Map<String, ValidationResult.Option> options,
                                                  UpdaterUI ui) throws IOException {
    File toDir = toBaseDir(rootDir);
    List<PatchAction> actionsToApply = new ArrayList<>(myActions.size());

    try {
      for (PatchAction each : myActions) {
        ui.checkCancelled();
        if (each.shouldApply(toDir, options)) {
          actionsToApply.add(each);
        }
      }

      if (actionsToApply.isEmpty()) {
        LOG.info("nothing to apply");
        return new PatchFileCreator.ApplicationResult(false, Collections.emptyList());
      }

      if (backupDir != null) {
        File _backupDir = backupDir;
        forEach(actionsToApply, "Backing up files...", ui, action -> action.backup(toDir, _backupDir));
      }
      else {
        //noinspection SSBasedInspection
        List<PatchAction> specialActions = actionsToApply.stream().filter(PatchAction::mandatoryBackup).collect(Collectors.toList());
        if (!specialActions.isEmpty()) {
          backupDir = Utils.getTempFile("partial_backup");
          if (!backupDir.mkdir()) throw new IOException("Cannot create a backup directory: " + backupDir);
          File _backupDir = backupDir;
          forEach(specialActions, "Preparing update...", ui, action -> action.backup(toDir, _backupDir));
        }
      }
    }
    catch (OperationCancelledException e) {
      LOG.log(Level.WARNING, "cancelled", e);
      return new PatchFileCreator.ApplicationResult(false, Collections.emptyList());
    }

    List<PatchAction> appliedActions = new ArrayList<>(actionsToApply.size());

    try {
      File _backupDir = backupDir;
      forEach(actionsToApply, "Applying patch...", ui, action -> {
        if (action instanceof CreateAction && !new File(toDir, action.getPath()).getParentFile().exists()) {
          LOG.info("Create action: " + action.getPath() + " skipped. The parent directory is absent.");
        }
        else if (action instanceof UpdateAction && !new File(toDir, action.getPath()).getParentFile().exists()) {
          LOG.info("Update action: " + action.getPath() + " skipped. The parent directory is absent.");
        }
        else {
          appliedActions.add(action);
          action.apply(patchFile, _backupDir, toDir);
        }
      });
    }
    catch (OperationCancelledException e) {
      LOG.log(Level.WARNING, "cancelled", e);
      return new PatchFileCreator.ApplicationResult(false, appliedActions);
    }
    catch (Throwable t) {
      LOG.log(Level.SEVERE, "apply failed", t);
      return new PatchFileCreator.ApplicationResult(false, appliedActions, t);
    }

    try {
      // on macOS, we need to update the bundle timestamp to reset Info.plist caches
      Files.setLastModifiedTime(toDir.toPath(), FileTime.from(Instant.now()));
    }
    catch (IOException e) {
      LOG.log(Level.WARNING, "setLastModified: " + toDir, e);
    }

    return new PatchFileCreator.ApplicationResult(true, appliedActions);
  }

  public void revert(List<? extends PatchAction> actions, File backupDir, File rootDir, UpdaterUI ui) throws IOException {
    LOG.info("Reverting... [" + actions.size() + " actions]");
    ui.startProcess("Reverting...");

    List<PatchAction> reverse = new ArrayList<>(actions);
    Collections.reverse(reverse);
    File toDir = toBaseDir(rootDir);

    for (int i = 0; i < reverse.size(); i++) {
      reverse.get(i).revert(toDir, backupDir);
      ui.setProgress((i + 1) * 100 / reverse.size());
    }
  }

  private static void forEach(List<? extends PatchAction> actions,
                              String title,
                              UpdaterUI ui,
                              ActionsProcessor processor) throws OperationCancelledException, IOException {
    LOG.info(title + " [" + actions.size() + " actions]");
    ui.startProcess(title);
    ui.checkCancelled();

    for (int i = 0; i < actions.size(); i++) {
      PatchAction each = actions.get(i);
      ui.checkCancelled();
      processor.forEach(each);
      ui.setProgress((i + 1) * 100 / actions.size());
    }

    ui.checkCancelled();
  }

  public long digestFile(File toFile, boolean normalize) throws IOException {
    if (!myIsBinary && Utils.isZipFile(toFile.getName())) {
      return Digester.digestZipFile(toFile);
    }
    else {
      return Digester.digestRegularFile(toFile, normalize);
    }
  }

  public Map<String, Long> digestFiles(File dir, Set<String> ignoredFiles, boolean normalize) throws IOException {
    Map<String, Long> result = new LinkedHashMap<>();
    Utils.collectRelativePaths(dir.toPath()).parallelStream().forEachOrdered(path -> {
      if (!ignoredFiles.contains(path)) {
        try {
          long hash = digestFile(new File(dir, path), normalize);
          synchronized (result) {
            result.put(path, hash);
          }
        }
        catch (IOException e) { throw new UncheckedIOException(e); }
      }
    });
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

  public int getTimeout() {
    return myTimeout;
  }

  @FunctionalInterface
  private interface ActionsProcessor {
    void forEach(PatchAction action) throws IOException;
  }
}
