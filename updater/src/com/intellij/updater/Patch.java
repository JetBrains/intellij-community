package com.intellij.updater;

import java.io.*;
import java.util.*;
import java.util.zip.ZipFile;

public class Patch {
  private List<PatchAction> myActions = new ArrayList<PatchAction>();

  private static final int CREATE_ACTION_KEY = 1;
  private static final int UPDATE_ACTION_KEY = 2;
  private static final int UPDATE_ZIP_ACTION_KEY = 3;
  private static final int DELETE_ACTION_KEY = 4;

  public Patch(File olderDir,
               File newerDir,
               List<String> ignoredFiles,
               List<String> criticalFiles,
               List<String> optionalFiles,
               UpdaterUI ui) throws IOException, OperationCancelledException {
    calculateActions(olderDir, newerDir, ignoredFiles, criticalFiles, optionalFiles, ui);
  }

  public Patch(InputStream patchIn) throws IOException {
    read(patchIn);
  }

  private void calculateActions(File olderDir,
                                File newerDir,
                                List<String> ignoredFiles,
                                List<String> criticalFiles,
                                List<String> optionalFiles,
                                UpdaterUI ui)
    throws IOException, OperationCancelledException {
    DiffCalculator.Result diff;

    ui.startProcess("Calculating difference...");
    ui.checkCancelled();

    diff = DiffCalculator.calculate(Digester.digestFiles(olderDir, ignoredFiles, ui),
                                    Digester.digestFiles(newerDir, ignoredFiles, ui));

    List<PatchAction> tempActions = new ArrayList<PatchAction>();

    // 'delete' actions before 'create' actions to prevent newly created files to be deleted if the names differ only on case.
    for (Map.Entry<String, Long> each : diff.filesToDelete.entrySet()) {
      tempActions.add(new DeleteAction(each.getKey(), each.getValue()));
    }

    for (String each : diff.filesToCreate) {
      tempActions.add(new CreateAction(each));
    }

    for (Map.Entry<String, Long> each : diff.filesToUpdate.entrySet()) {
      if (Utils.isZipFile(each.getKey())) {
        tempActions.add(new UpdateZipAction(each.getKey(), each.getValue()));
      }
      else {
        tempActions.add(new UpdateAction(each.getKey(), each.getValue()));
      }
    }

    ui.startProcess("Preparing actions...");
    ui.checkCancelled();

    for (PatchAction each : tempActions) {
      ui.setStatus(each.getPath());
      ui.checkCancelled();

      if (!each.calculate(olderDir, newerDir)) continue;
      myActions.add(each);
      each.setCritical(criticalFiles.contains(each.getPath()));
      each.setOptional(optionalFiles.contains(each.getPath()));
    }
  }

  public List<PatchAction> getActions() {
    return myActions;
  }

  public void write(OutputStream out) throws IOException {
    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataOutputStream dataOut = new DataOutputStream(out);
    try {
      dataOut.writeInt(myActions.size());

      for (PatchAction each : myActions) {
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
        else {
          throw new RuntimeException("Unknown action " + each);
        }
        dataOut.writeInt(key);
        each.write(dataOut);
      }
    }
    finally {
      dataOut.flush();
    }
  }

  private void read(InputStream patchIn) throws IOException {
    List<PatchAction> newActions = new ArrayList<PatchAction>();

    @SuppressWarnings("IOResourceOpenedButNotSafelyClosed") DataInputStream in = new DataInputStream(patchIn);
    int size = in.readInt();

    while (size-- > 0) {
      int key = in.readInt();
      PatchAction a;
      switch (key) {
        case CREATE_ACTION_KEY:
          a = new CreateAction(in);
          break;
        case UPDATE_ACTION_KEY:
          a = new UpdateAction(in);
          break;
        case UPDATE_ZIP_ACTION_KEY:
          a = new UpdateZipAction(in);
          break;
        case DELETE_ACTION_KEY:
          a = new DeleteAction(in);
          break;
        default:
          throw new RuntimeException("Unknown action type " + key);
      }
      newActions.add(a);
    }

    myActions = newActions;
  }

  public List<ValidationResult> validate(final File toDir, UpdaterUI ui) throws IOException, OperationCancelledException {
    final LinkedHashSet<String> files = Utils.collectRelativePaths(toDir);
    final List<ValidationResult> result = new ArrayList<ValidationResult>();

    forEach(myActions, "Validating installation...", ui, true,
            new ActionsProcessor() {
              public void forEach(PatchAction each) throws IOException {
                ValidationResult validationResult = each.validate(toDir);
                if (validationResult != null) result.add(validationResult);
                files.remove(each.getPath());
              }
            });

    //for (String each : files) {
    //  result.add(new ValidationResult(ValidationResult.Kind.INFO,
    //                                  each,
    //                                  ValidationResult.Action.NO_ACTION,
    //                                  ValidationResult.MANUALLY_ADDED_MESSAGE,
    //                                  ValidationResult.Option.KEEP, ValidationResult.Option.DELETE));
    //}

    return result;
  }

  public ApplicationResult apply(final ZipFile patchFile,
                                 final File toDir,
                                 final File backupDir,
                                 final Map<String, ValidationResult.Option> options,
                                 UpdaterUI ui) throws IOException, OperationCancelledException {

    List<PatchAction> actionsToProcess = new ArrayList<PatchAction>();
    for (PatchAction each : myActions) {
      if (each.shouldApply(toDir, options)) actionsToProcess.add(each);
    }

    forEach(actionsToProcess, "Backing up files...", ui, true,
            new ActionsProcessor() {
              public void forEach(PatchAction each) throws IOException {
                each.backup(toDir, backupDir);
              }
            });

    final List<PatchAction> appliedActions = new ArrayList<PatchAction>();
    boolean shouldRevert = false;
    boolean cancelled = false;
    try {
      forEach(actionsToProcess, "Applying patch...", ui, true,
              new ActionsProcessor() {
                public void forEach(PatchAction each) throws IOException {
                  appliedActions.add(each);
                  each.apply(patchFile, toDir);
                }
              });
    }
    catch (OperationCancelledException e) {
      shouldRevert = true;
      cancelled = true;
    }
    catch (Throwable e) {
      shouldRevert = true;
      ui.showError(e);
    }

    if (shouldRevert) {
      revert(appliedActions, backupDir, toDir, ui);
      appliedActions.clear();

      if (cancelled) throw new OperationCancelledException();
    }

    // on OS X we need to update bundle timestamp to reset Info.plist caches.
    toDir.setLastModified(System.currentTimeMillis());

    return new ApplicationResult(appliedActions);
  }

  public void revert(List<PatchAction> actions, final File backupDir, final File toDir, UpdaterUI ui)
    throws OperationCancelledException, IOException {
    Collections.reverse(actions);
    forEach(actions, "Reverting...", ui, false,
            new ActionsProcessor() {
              public void forEach(PatchAction each) throws IOException {
                each.revert(toDir, backupDir);
              }
            });
  }

  private static void forEach(List<PatchAction> actions, String title, UpdaterUI ui, boolean canBeCancelled, ActionsProcessor processor)
    throws OperationCancelledException, IOException {
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

  public interface ActionsProcessor {
    void forEach(PatchAction each) throws IOException;
  }

  public static class ApplicationResult {
    final boolean applied;
    final List<PatchAction> appliedActions;

    public ApplicationResult(List<PatchAction> appliedActions) {
      this.applied = !appliedActions.isEmpty();
      this.appliedActions = appliedActions;
    }
  }
}
