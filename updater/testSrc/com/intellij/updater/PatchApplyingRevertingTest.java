// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static com.intellij.updater.UpdaterTestCase.*;
import static org.assertj.core.api.Assertions.assertThat;

@UpdaterTest
abstract class PatchApplyingRevertingTest {
  static final class StandardModeTest extends PatchApplyingRevertingTest { }
  static final class NoBackupTest extends PatchApplyingRevertingTest { }

  private static final class CancellableUI extends ConsoleUpdaterUI {
    private boolean cancelled = false;

    private CancellableUI() {
      super(false);
    }

    @Override
    public void checkCancelled() throws OperationCancelledException {
      if (cancelled) throw new OperationCancelledException();
    }
  }

  @TempDir Path tempDir;
  @UpdaterTestData Path dataDir;

  private final CancellableUI testUI = new CancellableUI();
  private final boolean doBackup = this instanceof StandardModeTest;

  private PatchApplyingRevertingTest() { }

  @BeforeEach void clearUIState() {
    testUI.cancelled = false;
  }

  @Test void creatingAndApplying() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);

    assertAppliedAndReverted(dirs);
  }

  @Test void creatingAndApplyingStrict() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true));

    assertAppliedAndReverted(dirs, patchFile);
  }

  @Test void creatingAndApplyingOnADifferentRoot() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true).setRoot("bin/"));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.resolve("bin").toFile(), testUI);

    assertAppliedAndReverted(dirs, preparationResult);
  }

  @Test void creatingAndFailingOnADifferentRoot() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true).setRoot("bin/"));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.resolve("bin").toFile(), testUI);
    preparationResult.patch.getActions().add(new MyFailOnApplyPatchAction(preparationResult.patch));

    assertNotApplied(dirs, preparationResult);
  }

  @Test void reverting() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);
    preparationResult.patch.getActions().add(new MyFailOnApplyPatchAction(preparationResult.patch));

    assertNotApplied(dirs, preparationResult);
  }

  @Test @EnabledOnOs(OS.WINDOWS) void revertedWhenFileToDeleteIsLocked() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    doLockedFileTest(dirs);
  }

  @Test @EnabledOnOs(OS.WINDOWS) void revertedWhenFileToUpdateIsLocked() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Files.writeString(dirs.newDir.resolve("bin/idea.bat"), "new text");
    doLockedFileTest(dirs);
  }

  private void doLockedFileTest(Directories dirs) throws Exception {
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir));

    try (var raf = new RandomAccessFile(dirs.oldDir.resolve("bin/idea.bat").toFile(), "rw")) {
      var b = raf.read();
      raf.seek(0);
      raf.write(b);

      var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);
      assertNotApplied(dirs, preparationResult);
    }
  }

  @Test void revertedWhenDeleteFailed() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    var original = findAction(preparationResult.patch, "bin/idea.bat");
    assertThat(original).isInstanceOf(DeleteAction.class);

    var actions = preparationResult.patch.getActions();
    actions.set(actions.indexOf(original), new DeleteAction(preparationResult.patch, original.getPath(), original.getChecksum()) {
      @Override
      protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
        throw new IOException("dummy exception");
      }
    });

    assertNotApplied(dirs, preparationResult);
  }

  @Test void revertedWhenUpdateFailed() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Files.writeString(dirs.newDir.resolve("bin/idea.bat"), "new text");
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    var original = findAction(preparationResult.patch, "bin/idea.bat");
    assertThat(original).isInstanceOf(UpdateAction.class);

    var actions = preparationResult.patch.getActions();
    actions.set(actions.indexOf(original), new UpdateAction(preparationResult.patch, original.getPath(), original.getChecksum()) {
      @Override
      protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
        throw new IOException("dummy exception");
      }
    });

    assertNotApplied(dirs, preparationResult);
  }

  @Test void cancelledAtBackingUp() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);
    var actions = preparationResult.patch.getActions();
    actions.add(new MyFailOnApplyPatchAction(preparationResult.patch) {
      @Override
      protected void doBackup(File toFile, File backupFile) {
        testUI.cancelled = true;
      }
    });

    assertNotApplied(dirs, preparationResult);
  }

  @Test void cancelledAtApplying() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);
    var actions = preparationResult.patch.getActions();
    actions.add(new MyFailOnApplyPatchAction(preparationResult.patch) {
      @Override
      protected void doApply(ZipFile patchFile, File backupDir, File toFile) {
        testUI.cancelled = true;
      }
    });

    assertNotApplied(dirs, preparationResult);
  }

  @Test void applyingWithAbsentFileToDelete() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Files.delete(dirs.oldDir.resolve("bin/idea.bat"));
    assertAppliedAndReverted(dirs);
  }

  @Test void applyingWithAbsentFileToUpdateStrict() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true));
    Files.delete(dirs.oldDir.resolve("lib/annotations.jar"));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).containsExactly(
      new ValidationResult(ValidationResult.Kind.ERROR,
        "lib/annotations.jar",
        ValidationResult.Action.UPDATE,
        "Absent",
        ValidationResult.Option.NONE));
  }

  @Test void applyingWithAbsentOptionalFile() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Files.writeString(dirs.newDir.resolve("bin/idea.bat"), "new content");
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setOptionalFiles(List.of("bin/idea.bat")));
    Files.delete(dirs.oldDir.resolve("bin/idea.bat"));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndReverted(dirs, preparationResult, (original, target) -> target.remove("bin/idea.bat"));
  }

  @Test void applyingWithAbsentOptionalDirectory() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Files.createDirectory(dirs.oldDir.resolve("opt"));
    Files.writeString(dirs.oldDir.resolve("opt/file.txt"), "previous content");
    Files.createDirectory(dirs.newDir.resolve("opt"));
    Files.writeString(dirs.newDir.resolve("opt/file.txt"), "new content");
    Files.writeString(dirs.newDir.resolve("opt/another.txt"), "content");
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setOptionalFiles(List.of("opt/file.txt")));
    Utils.delete(dirs.oldDir.resolve("opt"));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndReverted(dirs, preparationResult, (original, target) -> {
      target.remove("opt/");
      target.remove("opt/file.txt");
      target.remove("opt/another.txt");
    });
  }

  @Test void applyingWithAbsentOptionalCriticalFile() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Files.writeString(dirs.oldDir.resolve("bin/idea.bat"), "old content");
    Files.writeString(dirs.newDir.resolve("bin/idea.bat"), "new content");
    var files = List.of("bin/idea.bat");
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setOptionalFiles(files).setCriticalFiles(files));
    Files.delete(dirs.oldDir.resolve("bin/idea.bat"));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndReverted(dirs, preparationResult);
  }

  @Test void applyingWithModifiedOptionalCriticalFile() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Files.writeString(dirs.oldDir.resolve("bin/idea.bat"), "old content");
    Files.writeString(dirs.newDir.resolve("bin/idea.bat"), "new content");
    var files = List.of("bin/idea.bat");
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setOptionalFiles(files).setCriticalFiles(files));
    Files.writeString(dirs.oldDir.resolve("bin/idea.bat"), "unexpected content");
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndReverted(dirs, preparationResult);
  }

  @Test void revertingWithAbsentFileToDelete() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir));
    Files.delete(dirs.oldDir.resolve("bin/idea.bat"));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);
    preparationResult.patch.getActions().add(new MyFailOnApplyPatchAction(preparationResult.patch));

    assertNotApplied(dirs, preparationResult);
  }

  @Test void applyingWithCriticalFiles() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setCriticalFiles(List.of("lib/annotations.jar")));

    assertAppliedAndReverted(dirs, patchFile);
  }

  @Test void applyingWithModifiedCriticalFiles() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true).setCriticalFiles(List.of("lib/annotations.jar")));
    modifyFile(dirs.oldDir.resolve("lib/annotations.jar"));

    assertAppliedAndReverted(dirs, patchFile);
  }

  @Test void applyingWithRemovedCriticalFiles() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true).setCriticalFiles(List.of("lib/annotations.jar")));
    Files.delete(dirs.oldDir.resolve("lib/annotations.jar"));

    assertAppliedAndReverted(dirs, patchFile);
  }

  @Test void applyingWithRemovedNonCriticalFilesWithStrict() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true));
    Files.delete(dirs.oldDir.resolve("lib/annotations.jar"));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).containsExactly(
      new ValidationResult(ValidationResult.Kind.ERROR,
        "lib/annotations.jar",
        ValidationResult.Action.UPDATE,
        "Absent",
        ValidationResult.Option.NONE));
  }

  @Test void applyingWithRemovedNonCriticalFilesWithoutStrict() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir));
    Files.delete(dirs.oldDir.resolve("lib/annotations.jar"));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).containsExactly(
      new ValidationResult(ValidationResult.Kind.ERROR,
        "lib/annotations.jar",
        ValidationResult.Action.UPDATE,
        "Absent",
        ValidationResult.Option.IGNORE));
  }

  @Test void applyingWithModifiedCriticalFilesAndDifferentRoot() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var spec = createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true).setRoot("lib/").setCriticalFiles(List.of("lib/annotations.jar"));
    var patchFile = createPatch(spec);
    modifyFile(dirs.oldDir.resolve("lib/annotations.jar"));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.resolve("lib/").toFile(), testUI);

    assertAppliedAndReverted(dirs, preparationResult);
  }

  @Test void applyingWithCaseChangedNames() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Files.move(dirs.oldDir.resolve("Readme.txt"), dirs.oldDir.resolve("README.txt"));

    assertAppliedAndReverted(dirs);
  }

  @Test void creatingAndApplyingWhenDirectoryBecomesFile() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var file = dirs.oldDir.resolve("Readme.txt");
    Files.delete(file);
    Files.createDirectory(file);
    Files.createFile(file.resolve("subFile.txt"));
    Files.createFile(Files.createDirectory(file.resolve("subDir")).resolve("subFile.txt"));
    Files.copy(dirs.oldDir.resolve("lib/boot.jar"), dirs.oldDir.resolve("lib/boot_with_directory_becomes_file.jar"), StandardCopyOption.REPLACE_EXISTING);

    assertAppliedAndReverted(dirs);
  }

  @Test void creatingAndApplyingWhenFileBecomesDirectory() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var file = dirs.oldDir.resolve("bin");
    Utils.delete(file);
    Files.createFile(file);
    Files.copy(dirs.oldDir.resolve("lib/boot_with_directory_becomes_file.jar"), dirs.oldDir.resolve("lib/boot.jar"), StandardCopyOption.REPLACE_EXISTING);

    assertAppliedAndReverted(dirs);
  }

  @Test void consideringOptions() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);
    var options = preparationResult.patch.getActions().stream().collect(Collectors.toMap(PatchAction::getPath, a -> ValidationResult.Option.IGNORE));

    assertNotApplied(dirs, preparationResult, options);
  }

  @Test void applyWhenCommonFileChanges() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir));
    Files.copy(dirs.oldDir.resolve("lib/bootstrap.jar"), dirs.oldDir.resolve("lib/boot.jar"), StandardCopyOption.REPLACE_EXISTING);
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndReverted(dirs, preparationResult, (original, target) -> target.put("lib/boot.jar", BOOTSTRAP_JAR));
  }

  @Test void applyWhenCommonFileChangesStrict() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true));
    Files.copy(dirs.oldDir.resolve("lib/bootstrap.jar"), dirs.oldDir.resolve("lib/boot.jar"), StandardCopyOption.REPLACE_EXISTING);
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).containsExactly(
      new ValidationResult(ValidationResult.Kind.ERROR,
        "lib/boot.jar",
        ValidationResult.Action.VALIDATE,
        "Modified",
        ValidationResult.Option.NONE));
  }

  @Test void applyWhenCommonFileChangesStrictFile() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrictFiles(List.of("lib/annotations.jar")));
    Files.copy(dirs.oldDir.resolve("lib/bootstrap.jar"), dirs.oldDir.resolve("lib/annotations.jar"), StandardCopyOption.REPLACE_EXISTING);
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).containsExactly(
      new ValidationResult(ValidationResult.Kind.ERROR,
        "lib/annotations.jar",
        ValidationResult.Action.UPDATE,
        "Modified",
        ValidationResult.Option.NONE));
  }

  @Test void applyWhenNewFileExists() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir));
    Files.copy(dirs.oldDir.resolve("Readme.txt"), dirs.oldDir.resolve("new_file.txt"));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndReverted(dirs, preparationResult, (original, target) -> target.put("new_file.txt", README_TXT));
  }

  @Test void applyWhenNewFileExistsStrict() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true).setDeleteFiles(List.of("lib/java_pid.*\\.hprof")));
    Files.writeString(dirs.oldDir.resolve("new_file.txt"), "hello");
    Files.writeString(dirs.oldDir.resolve("lib/java_pid1234.hprof"), "bye!");
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).containsExactly(
      new ValidationResult(ValidationResult.Kind.CONFLICT,
        "new_file.txt",
        ValidationResult.Action.VALIDATE,
        "Unexpected file",
        ValidationResult.Option.DELETE));
    assertAppliedAndReverted(dirs, preparationResult);
  }

  @Test void applyWhenNewDeletableFileExistsStrict() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true).setDeleteFiles(List.of("lib/java_pid.*\\.hprof")));
    Files.writeString(dirs.oldDir.resolve("lib/java_pid1234.hprof"), "bye!");
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndReverted(dirs, preparationResult);
  }

  @Test void applyWhenNewDirectoryExistsStrict() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Utils.writeString(dirs.oldDir.resolve("delete/delete_me.txt"), "bye!");
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true));
    Utils.writeString(dirs.oldDir.resolve("unexpected_new_dir/unexpected.txt"), "bye!");
    Files.createDirectory(dirs.oldDir.resolve("newDir"));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).containsExactly(
      new ValidationResult(ValidationResult.Kind.CONFLICT,
        "unexpected_new_dir/unexpected.txt",
        ValidationResult.Action.VALIDATE,
        "Unexpected file",
        ValidationResult.Option.DELETE),
      new ValidationResult(ValidationResult.Kind.CONFLICT,
        "unexpected_new_dir/",
        ValidationResult.Action.VALIDATE,
        "Unexpected file",
        ValidationResult.Option.DELETE),
      new ValidationResult(ValidationResult.Kind.CONFLICT,
        "newDir/",
        ValidationResult.Action.CREATE,
        "Already exists",
        ValidationResult.Option.REPLACE));

    Utils.delete(dirs.oldDir.resolve("newDir"));
    assertAppliedAndReverted(dirs, preparationResult);
  }

  @Test void moveFileByContent() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Utils.writeString(dirs.oldDir.resolve("move/from/this/directory/move.me"), "old_content");
    Utils.writeString(dirs.oldDir.resolve("a/deleted/file/that/is/a/copy/move.me"), "new_content");
    Utils.writeString(dirs.newDir.resolve("move/to/this/directory/move.me"), "new_content");
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true));
    var hash = Digester.digestStream(new ByteArrayInputStream("new_content".getBytes(StandardCharsets.UTF_8)));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(findAction(preparationResult.patch, "move/to/this/directory/move.me"))
      .isEqualTo(new UpdateAction(preparationResult.patch, "move/to/this/directory/move.me", "a/deleted/file/that/is/a/copy/move.me", hash, true));
    assertAppliedAndReverted(dirs, preparationResult);
  }

  @Test void moveCriticalFileByContent() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Utils.writeString(dirs.oldDir.resolve("move/from/this/directory/move.me"), "old_content");
    Utils.writeString(dirs.oldDir.resolve("a/deleted/file/that/is/a/copy/move.me"), "new_content");
    Utils.writeString(dirs.newDir.resolve("move/to/this/directory/move.me"), "new_content");
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true).setCriticalFiles(List.of("a/deleted/file/that/is/a/copy/move.me")));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(findAction(preparationResult.patch, "move/to/this/directory/move.me")).isInstanceOf(CreateAction.class);
    assertAppliedAndReverted(dirs, preparationResult);
  }

  @Test void noMoveFromDirectoryToFile() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Files.createDirectories(dirs.oldDir.resolve("from/move.me"));
    Utils.writeString(dirs.newDir.resolve("move/to/move.me"), "different");
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true));
    // creating a patch would have crashed if the directory had been chosen
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).isEmpty();
    assertThat(findAction(preparationResult.patch, "move/to/move.me")).isInstanceOf(CreateAction.class);
    assertThat(findAction(preparationResult.patch, "from/move.me/")).isInstanceOf(DeleteAction.class);
    assertAppliedAndReverted(dirs, preparationResult);
  }

  @Test void moveFileByLocation() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Utils.writeString(dirs.oldDir.resolve("move/from/this/directory/move.me"), "they");
    Utils.writeString(dirs.oldDir.resolve("not/from/this/one/move.me"), "are");
    Utils.writeString(dirs.newDir.resolve("move/to/this/directory/move.me"), "different");
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setStrict(true));
    var hash = Digester.digestStream(new ByteArrayInputStream("they".getBytes(StandardCharsets.UTF_8)));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(findAction(preparationResult.patch, "move/to/this/directory/move.me"))
      .isEqualTo(new UpdateAction(preparationResult.patch, "move/to/this/directory/move.me", "move/from/this/directory/move.me", hash, false));
    assertAppliedAndReverted(dirs, preparationResult);
  }

  @Test void symlinkAdded() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Files.createSymbolicLink(dirs.newDir.resolve("Readme.link"), Path.of("Readme.txt"));

    assertAppliedAndReverted(dirs);
  }

  @Test void symlinkRemoved() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Files.createSymbolicLink(dirs.oldDir.resolve("Readme.link"), Path.of("Readme.txt"));

    assertAppliedAndReverted(dirs);
  }

  @SuppressWarnings("DuplicateExpressions")
  @Test void symlinkRenamed() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Files.createSymbolicLink(dirs.oldDir.resolve("Readme.link"), Path.of("Readme.txt"));
    Files.createSymbolicLink(dirs.newDir.resolve("Readme.lnk"), Path.of("Readme.txt"));

    assertAppliedAndReverted(dirs);
  }

  @Test void symlinkRetargeted() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    Files.createSymbolicLink(dirs.oldDir.resolve("Readme.link"), Path.of("Readme.txt"));
    Files.createSymbolicLink(dirs.newDir.resolve("Readme.link"), Path.of("./Readme.txt"));

    assertAppliedAndReverted(dirs);
  }

  @Test void zipFileMove() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Utils.move(dirs.newDir.resolve("lib/annotations.jar"), dirs.newDir.resolve("lib/redist/annotations.jar"), false);

    assertAppliedAndReverted(dirs);
  }

  @Test void zipFileMoveWithUpdate() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Files.delete(dirs.newDir.resolve("lib/annotations.jar"));
    Utils.copy(dataDir.resolve("lib/annotations_changed.jar"), dirs.newDir.resolve("lib/redist/annotations.jar"), false);

    assertAppliedAndReverted(dirs);
  }

  @Test void readOnlyFilesAreDeletable() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var file = dirs.oldDir.resolve("bin/read_only_to_delete");
    Files.writeString(file, "bye");
    setReadOnly(file);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndReverted(dirs, preparationResult);
  }

  @Test @DisabledOnOs(OS.WINDOWS) void executableFlagChange() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Files.writeString(dirs.oldDir.resolve("bin/to_become_executable"), "to_become_executable");
    Files.writeString(dirs.oldDir.resolve("bin/to_become_plain"), "to_become_plain");
    Utils.setExecutable(dirs.oldDir.resolve("bin/to_become_plain"));
    Files.writeString(dirs.newDir.resolve("bin/to_become_executable"), "to_become_executable");
    Files.writeString(dirs.newDir.resolve("bin/to_become_plain"), "to_become_plain");
    Utils.setExecutable(dirs.newDir.resolve("bin/to_become_executable"));

    assertAppliedAndReverted(dirs);
  }

  @Test void fileToSymlinks() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Files.move(dirs.newDir.resolve("Readme.txt"), dirs.newDir.resolve("Readme.md"));
    Files.createSymbolicLink(dirs.newDir.resolve("Readme.txt"), Path.of("Readme.md"));

    assertAppliedAndReverted(dirs);
  }

  @SuppressWarnings("DuplicateExpressions")
  @Test void multipleDirectorySymlinks() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);

    randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Libraries/lib1.dylib"));
    randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Libraries/lib2.dylib"));
    randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Resources/r1.bin"));
    randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Resources/r2.bin"));
    Files.createSymbolicLink(dirs.oldDir.resolve("A.framework/Versions/Current"), Path.of("A"));
    Files.createSymbolicLink(dirs.oldDir.resolve("A.framework/Libraries"), Path.of("Versions/Current/Libraries"));
    Files.createSymbolicLink(dirs.oldDir.resolve("A.framework/Resources"), Path.of("Versions/Current/Resources"));
    Files.createDirectories(dirs.oldDir.resolve("Home/Frameworks"));
    Files.createSymbolicLink(dirs.oldDir.resolve("Home/Frameworks/A.framework"), Path.of("../../A.framework"));

    randomFile(dirs.newDir.resolve("A.framework/Versions/A/Libraries/lib1.dylib"));
    randomFile(dirs.newDir.resolve("A.framework/Versions/A/Libraries/lib2.dylib"));
    randomFile(dirs.newDir.resolve("A.framework/Versions/A/Resources/r1.bin"));
    randomFile(dirs.newDir.resolve("A.framework/Versions/A/Resources/r2.bin"));
    randomFile(dirs.newDir.resolve("A.framework/Versions/B/Libraries/lib1.dylib"));
    randomFile(dirs.newDir.resolve("A.framework/Versions/B/Libraries/lib2.dylib"));
    randomFile(dirs.newDir.resolve("A.framework/Versions/B/Resources/r1.bin"));
    randomFile(dirs.newDir.resolve("A.framework/Versions/B/Resources/r2.bin"));
    Files.createSymbolicLink(dirs.newDir.resolve("A.framework/Versions/Previous"), Path.of("A"));
    Files.createSymbolicLink(dirs.newDir.resolve("A.framework/Versions/Current"), Path.of("B"));
    Files.createSymbolicLink(dirs.newDir.resolve("A.framework/Libraries"), Path.of("Versions/Current/Libraries"));
    Files.createSymbolicLink(dirs.newDir.resolve("A.framework/Resources"), Path.of("Versions/Current/Resources"));

    assertAppliedAndReverted(dirs);
  }

  @Test void symlinksToFilesAndDirectories() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);

    randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Libraries/lib1.dylib"));
    randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Libraries/lib2.dylib"));
    randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Resources/r1/res.bin"));
    randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Resources/r2/res.bin"));
    Files.createSymbolicLink(dirs.oldDir.resolve("A.framework/Versions/Current"), Path.of("A"));
    Files.createSymbolicLink(dirs.oldDir.resolve("A.framework/Libraries"), Path.of("Versions/Current/Libraries"));
    Files.createSymbolicLink(dirs.oldDir.resolve("A.framework/Resources"), Path.of("Versions/Current/Resources"));

    randomFile(dirs.newDir.resolve("A.framework/Libraries/lib1.dylib"));
    randomFile(dirs.newDir.resolve("A.framework/Libraries/lib2.dylib"));
    randomFile(dirs.newDir.resolve("A.framework/Resources/r1/res.bin"));
    randomFile(dirs.newDir.resolve("A.framework/Resources/r2/res.bin"));

    assertAppliedAndReverted(dirs);
  }

  @Test void creatingParentDirectoriesForMissingCriticalFiles() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    randomFile(dirs.oldDir.resolve("plugins/some/lib/plugin.jar"));
    randomFile(dirs.oldDir.resolve("plugins/other/lib/plugin.jar"));
    randomFile(dirs.newDir.resolve("plugins/some/lib/plugin.jar"));
    Utils.copy(dirs.oldDir.resolve("plugins/other/lib/plugin.jar"), dirs.newDir.resolve("plugins/other/lib/plugin.jar"), false);
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir).setCriticalFiles(List.of("plugins/some/lib/plugin.jar")));
    Utils.delete(dirs.oldDir.resolve("plugins/some"));
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);

    assertThat(preparationResult.validationResults).containsExactly(
      new ValidationResult(ValidationResult.Kind.CONFLICT,
                           "plugins/some/lib/plugin.jar",
                           ValidationResult.Action.UPDATE,
                           "Absent",
                           ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP)
    );
    assertAppliedAndReverted(dirs, preparationResult, (original, target) -> {
      original.put("plugins/some/", Digester.DIRECTORY);
      original.put("plugins/some/lib/", Digester.DIRECTORY);
    });
  }

  private static void modifyFile(Path file) throws IOException {
    try (var raf = new RandomAccessFile(file.toFile(), "rw")) {
      raf.seek(20);
      raf.write(42);
    }
  }

  private Path createPatch(PatchSpec spec) throws IOException {
    var patchFile = tempDir.resolve("patch.zip");
    PatchFileCreator.create(spec, patchFile.toFile(), null);
    assertThat(patchFile).isRegularFile();
    return patchFile;
  }

  @SuppressWarnings({"SSBasedInspection", "RedundantSuppression"})
  private static PatchAction findAction(Patch patch, String path) {
    return patch.getActions().stream().filter(a -> a.getPath().equals(path)).findFirst().orElse(null);
  }

  private void assertNotApplied(Directories dirs, PatchFileCreator.PreparationResult preparationResult) throws Exception {
    assertNotApplied(dirs, preparationResult, Map.of());
  }

  private void assertNotApplied(
    Directories dirs,
    PatchFileCreator.PreparationResult preparationResult,
    Map<String, ValidationResult.Option> options
  ) throws Exception {
    var patch = preparationResult.patch;
    var backupDir = doBackup ? tempDir.resolve("backup").toFile() : null;
    var original = digest(patch, dirs.oldDir);

    var applicationResult = PatchFileCreator.apply(preparationResult, options, backupDir, testUI);
    assertThat(applicationResult.applied).isFalse();

    if (doBackup) {
      PatchFileCreator.revert(preparationResult, applicationResult.appliedActions, backupDir, testUI);
      assertThat(digest(patch, dirs.oldDir)).containsExactlyEntriesOf(original);
    }
  }

  private void assertAppliedAndReverted(Directories dirs) throws Exception {
    var patchFile = createPatch(createPatchSpec(dirs.oldDir, dirs.newDir));
    assertAppliedAndReverted(dirs, patchFile);
  }

  private void assertAppliedAndReverted(Directories dirs, Path patchFile) throws IOException, OperationCancelledException {
    var preparationResult = PatchFileCreator.prepareAndValidate(patchFile.toFile(), dirs.oldDir.toFile(), testUI);
    assertAppliedAndReverted(dirs, preparationResult);
  }

  private void assertAppliedAndReverted(Directories dirs, PatchFileCreator.PreparationResult preparationResult) throws IOException {
    assertAppliedAndReverted(dirs, preparationResult, (original, target) -> {});
  }

  private void assertAppliedAndReverted(
    Directories dirs,
    PatchFileCreator.PreparationResult preparationResult,
    BiConsumer<Map<String, Long>, Map<String, Long>> adjuster
  ) throws IOException {
    var patch = preparationResult.patch;
    var original = digest(patch, dirs.oldDir);
    var target = digest(patch, dirs.newDir);
    adjuster.accept(original, target);
    var backupDir = doBackup ? tempDir.resolve("backup").toFile() : null;

    var options = new HashMap<String, ValidationResult.Option>();
    for (var each : preparationResult.validationResults) {
      if (patch.isStrict()) {
        assertThat(each.options).isNotEmpty().doesNotContain(ValidationResult.Option.NONE);
        options.put(each.path, each.options.get(0));
      }
      else {
        assertThat(each.kind).describedAs(each.toString()).isNotSameAs(ValidationResult.Kind.ERROR);
      }
    }

    var applicationResult = PatchFileCreator.apply(preparationResult, options, backupDir, testUI);
    if (applicationResult.error != null) {
      throw new AssertionError("patch failed", applicationResult.error);
    }
    assertThat(applicationResult.applied).isTrue();
    assertThat(digest(patch, dirs.oldDir)).containsExactlyEntriesOf(target);

    if (doBackup) {
      PatchFileCreator.revert(preparationResult, applicationResult.appliedActions, backupDir, testUI);
      assertThat(digest(patch, dirs.oldDir)).containsExactlyEntriesOf(original);
    }
  }

  private static Map<String, Long> digest(Patch patch, Path dir) throws IOException {
    return new TreeMap<>(patch.digestFiles(dir, Set.of()));
  }

  private static class MyFailOnApplyPatchAction extends PatchAction {
    MyFailOnApplyPatchAction(Patch patch) {
      super(patch, "_dummy_file_", Digester.INVALID);
    }

    @Override
    protected void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected ValidationResult validate(File toDir) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
      throw new IOException("dummy exception");
    }
  }
}
