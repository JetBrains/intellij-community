// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.testFramework.RunFirst;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static com.intellij.openapi.util.io.IoTestUtil.assumeSymLinkCreationIsSupported;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

@RunFirst
public abstract class PatchApplyingRevertingTest extends PatchTestCase {
  private File myFile;
  private PatchSpec myPatchSpec;
  private boolean myDoBackup;

  @Before
  @Override
  public void before() throws Exception {
    super.before();
    myFile = getTempFile("patch.zip");
    myPatchSpec = new PatchSpec()
      .setOldFolder(myOlderDir.getAbsolutePath())
      .setNewFolder(myNewerDir.getAbsolutePath())
      .setBinary(isBinary());
    myDoBackup = isBackup();
  }

  protected boolean isBinary() {
    return false;
  }

  protected boolean isBackup() {
    return true;
  }

  @Test
  public void testCreatingAndApplying() throws Exception {
    assertAppliedAndReverted();
  }

  @Test
  public void testCreatingAndApplyingStrict() throws Exception {
    myPatchSpec.setStrict(true);
    assertAppliedAndReverted();
  }

  @Test
  public void testCreatingAndApplyingOnADifferentRoot() throws Exception {
    myPatchSpec.setRoot("bin/");
    myPatchSpec.setStrict(true);
    createPatch();

    assertAppliedAndReverted(PatchFileCreator.prepareAndValidate(myFile, new File(myOlderDir, "bin"), TEST_UI));
  }

  @Test
  public void testCreatingAndFailingOnADifferentRoot() throws Exception {
    myPatchSpec.setRoot("bin/");
    myPatchSpec.setStrict(true);
    createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, new File(myOlderDir, "bin"), TEST_UI);
    preparationResult.patch.getActions().add(new MyFailOnApplyPatchAction(preparationResult.patch));
    assertNotApplied(preparationResult);
  }

  @Test
  public void testReverting() throws Exception {
    createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    preparationResult.patch.getActions().add(new MyFailOnApplyPatchAction(preparationResult.patch));
    assertNotApplied(preparationResult);
  }

  @Test
  public void testRevertedWhenFileToDeleteIsLocked() throws Exception {
    IoTestUtil.assumeWindows();
    doLockedFileTest();
  }

  @Test
  public void testRevertedWhenFileToUpdateIsLocked() throws Exception {
    IoTestUtil.assumeWindows();
    FileUtil.writeToFile(new File(myNewerDir, "bin/idea.bat"), "new text");
    doLockedFileTest();
  }

  private void doLockedFileTest() throws Exception {
    createPatch();

    try (RandomAccessFile raf = new RandomAccessFile(new File(myOlderDir, "bin/idea.bat"), "rw")) {
      int b = raf.read();
      raf.seek(0);
      raf.write(b);

      PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
      assertNotApplied(preparationResult);
    }
  }

  @Test
  public void testRevertedWhenDeleteFailed() throws Exception {
    createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    PatchAction original = findAction(preparationResult.patch, "bin/idea.bat");
    assertThat(original).isInstanceOf(DeleteAction.class);
    List<PatchAction> actions = preparationResult.patch.getActions();
    actions.set(actions.indexOf(original), new DeleteAction(preparationResult.patch, original.getPath(), original.getChecksum()) {
      @Override
      protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
        throw new IOException("dummy exception");
      }
    });

    assertNotApplied(preparationResult);
  }

  @Test
  public void testRevertedWhenUpdateFailed() throws Exception {
    FileUtil.writeToFile(new File(myNewerDir, "bin/idea.bat"), "new text");
    createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    PatchAction original = findAction(preparationResult.patch, "bin/idea.bat");
    assertThat(original).isInstanceOf(UpdateAction.class);
    List<PatchAction> actions = preparationResult.patch.getActions();
    actions.set(actions.indexOf(original), new UpdateAction(preparationResult.patch, original.getPath(), original.getChecksum()) {
      @Override
      protected void doApply(ZipFile patchFile, File backupDir, File toFile) throws IOException {
        throw new IOException("dummy exception");
      }
    });

    assertNotApplied(preparationResult);
  }

  @Test
  public void testCancelledAtBackingUp() throws Exception {
    createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    List<PatchAction> actions = preparationResult.patch.getActions();
    actions.add(new MyFailOnApplyPatchAction(preparationResult.patch) {
      @Override
      protected void doBackup(File toFile, File backupFile) {
        TEST_UI.cancelled = true;
      }
    });

    assertNotApplied(preparationResult);
  }

  @Test
  public void testCancelledAtApplying() throws Exception {
    createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    List<PatchAction> actions = preparationResult.patch.getActions();
    actions.add(new MyFailOnApplyPatchAction(preparationResult.patch) {
      @Override
      protected void doApply(ZipFile patchFile, File backupDir, File toFile) {
        TEST_UI.cancelled = true;
      }
    });

    assertNotApplied(preparationResult);
  }

  @Test
  public void testApplyingWithAbsentFileToDelete() throws Exception {
    createPatch();

    FileUtil.delete(new File(myOlderDir, "bin/idea.bat"));

    assertAppliedAndReverted();
  }

  @Test
  public void testApplyingWithAbsentFileToUpdateStrict() throws Exception {
    myPatchSpec.setStrict(true);
    createPatch();

    FileUtil.delete(new File(myOlderDir, "lib/annotations.jar"));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).containsExactly(
      new ValidationResult(ValidationResult.Kind.ERROR,
                           "lib/annotations.jar",
                           ValidationResult.Action.UPDATE,
                           ValidationResult.ABSENT_MESSAGE,
                           ValidationResult.Option.NONE));
  }

  @Test
  public void testApplyingWithAbsentOptionalFile() throws Exception {
    FileUtil.writeToFile(new File(myNewerDir, "bin/idea.bat"), "new content".getBytes(StandardCharsets.UTF_8));

    myPatchSpec.setOptionalFiles(Collections.singletonList("bin/idea.bat"));
    createPatch();

    FileUtil.delete(new File(myOlderDir, "bin/idea.bat"));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndReverted(preparationResult, expected -> expected.remove("bin/idea.bat"));
  }

  @Test
  public void testRevertingWithAbsentFileToDelete() throws Exception {
    createPatch();

    FileUtil.delete(new File(myOlderDir, "bin/idea.bat"));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    preparationResult.patch.getActions().add(new MyFailOnApplyPatchAction(preparationResult.patch));
    assertNotApplied(preparationResult);
  }

  @Test
  public void testApplyingWithCriticalFiles() throws Exception {
    myPatchSpec.setCriticalFiles(Collections.singletonList("lib/annotations.jar"));
    assertAppliedAndReverted();
  }

  @Test
  public void testApplyingWithModifiedCriticalFiles() throws Exception {
    myPatchSpec.setStrict(true);
    myPatchSpec.setCriticalFiles(Collections.singletonList("lib/annotations.jar"));
    createPatch();

    modifyFile(new File(myOlderDir, "lib/annotations.jar"));

    assertAppliedAndReverted();
  }

  @Test
  public void testApplyingWithModifiedCriticalFilesAndDifferentRoot() throws Exception {
    myPatchSpec.setStrict(true);
    myPatchSpec.setRoot("lib/");
    myPatchSpec.setCriticalFiles(Collections.singletonList("lib/annotations.jar"));
    createPatch();

    modifyFile(new File(myOlderDir, "lib/annotations.jar"));

    assertAppliedAndReverted(PatchFileCreator.prepareAndValidate(myFile, new File(myOlderDir, "lib/"), TEST_UI));
  }

  @Test
  public void testApplyingWithCaseChangedNames() throws Exception {
    FileUtil.rename(new File(myOlderDir, "Readme.txt"), new File(myOlderDir, "README.txt"));
    assertAppliedAndReverted();
  }

  @Test
  public void testCreatingAndApplyingWhenDirectoryBecomesFile() throws Exception {
    File file = new File(myOlderDir, "Readme.txt");
    FileUtil.delete(file);
    FileUtil.createDirectory(file);

    FileUtil.writeToFile(new File(file, "subFile.txt"), "");
    FileUtil.writeToFile(new File(file, "subDir/subFile.txt"), "");

    FileUtil.copy(new File(myOlderDir, "lib/boot.jar"), new File(myOlderDir, "lib/boot_with_directory_becomes_file.jar"));

    assertAppliedAndReverted();
  }

  @Test
  public void testCreatingAndApplyingWhenFileBecomesDirectory() throws Exception {
    File file = new File(myOlderDir, "bin");
    FileUtil.delete(file);
    FileUtil.writeToFile(file, "");

    FileUtil.copy(new File(myOlderDir, "lib/boot_with_directory_becomes_file.jar"), new File(myOlderDir, "lib/boot.jar"));

    assertAppliedAndReverted();
  }

  @Test
  public void testConsideringOptions() throws Exception {
    createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    Map<String, ValidationResult.Option> options =
      preparationResult.patch.getActions().stream().collect(Collectors.toMap(PatchAction::getPath, a -> ValidationResult.Option.IGNORE));
    assertNotApplied(preparationResult, options);
  }

  @Test
  public void testApplyWhenCommonFileChanges() throws Exception {
    createPatch();

    FileUtil.copy(new File(myOlderDir, "lib/bootstrap.jar"), new File(myOlderDir, "lib/boot.jar"));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).isEmpty();
    long hash = myPatchSpec.isBinary() ? CHECKSUMS.BOOTSTRAP_JAR_BIN : CHECKSUMS.BOOTSTRAP_JAR;
    assertAppliedAndReverted(preparationResult, expected -> expected.put("lib/boot.jar", hash));
  }

  @Test
  public void testApplyWhenCommonFileChangesStrict() throws Exception {
    myPatchSpec.setStrict(true);
    createPatch();

    FileUtil.copy(new File(myOlderDir, "lib/bootstrap.jar"), new File(myOlderDir, "lib/boot.jar"));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).containsExactly(
      new ValidationResult(ValidationResult.Kind.ERROR,
                           "lib/boot.jar",
                           ValidationResult.Action.VALIDATE,
                           ValidationResult.MODIFIED_MESSAGE,
                           ValidationResult.Option.NONE));
  }

  @Test
  public void testApplyWhenNewFileExists() throws Exception {
    createPatch();

    FileUtil.copy(new File(myOlderDir, "Readme.txt"), new File(myOlderDir, "new_file.txt"));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndReverted(preparationResult, expected -> expected.put("new_file.txt", CHECKSUMS.README_TXT));
  }

  @Test
  public void testApplyWhenNewFileExistsStrict() throws Exception {
    myPatchSpec.setStrict(true);
    myPatchSpec.setDeleteFiles(Collections.singletonList("lib/java_pid.*\\.hprof"));

    createPatch();

    FileUtil.writeToFile(new File(myOlderDir, "new_file.txt"), "hello");
    FileUtil.writeToFile(new File(myOlderDir, "lib/java_pid1234.hprof"), "bye!");

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).containsExactly(
      new ValidationResult(ValidationResult.Kind.CONFLICT,
                           "new_file.txt",
                           ValidationResult.Action.VALIDATE,
                           "Unexpected file",
                           ValidationResult.Option.DELETE));
    assertAppliedAndReverted(preparationResult);
  }

  @Test
  public void testApplyWhenNewDeletableFileExistsStrict() throws Exception {
    myPatchSpec.setStrict(true);
    myPatchSpec.setDeleteFiles(Collections.singletonList("lib/java_pid.*\\.hprof"));

    createPatch();

    FileUtil.writeToFile(new File(myOlderDir, "lib/java_pid1234.hprof"), "bye!");

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndReverted(preparationResult);
  }

  @Test
  public void testApplyWhenNewDirectoryExistsStrict() throws Exception {
    myPatchSpec.setStrict(true);
    FileUtil.writeToFile(new File(myOlderDir, "delete/delete_me.txt"), "bye!");

    createPatch();

    FileUtil.writeToFile(new File(myOlderDir, "unexpected_new_dir/unexpected.txt"), "bye!");

    FileUtil.createDirectory(new File(myOlderDir, "newDir"));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
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
                           ValidationResult.ALREADY_EXISTS_MESSAGE,
                           ValidationResult.Option.REPLACE));
    FileUtil.delete(new File(myOlderDir, "newDir"));
    assertAppliedAndReverted(preparationResult);
  }

  @Test
  public void testMoveFileByContent() throws Exception {
    myPatchSpec.setStrict(true);
    FileUtil.writeToFile(new File(myOlderDir, "move/from/this/directory/move.me"), "old_content");
    FileUtil.writeToFile(new File(myOlderDir, "a/deleted/file/that/is/a/copy/move.me"), "new_content");
    FileUtil.writeToFile(new File(myNewerDir, "move/to/this/directory/move.me"), "new_content");
    createPatch();

    long hash = Digester.digestStream(new ByteArrayInputStream("new_content".getBytes(StandardCharsets.UTF_8)));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(findAction(preparationResult.patch, "move/to/this/directory/move.me"))
      .isEqualTo(new UpdateAction(preparationResult.patch, "move/to/this/directory/move.me", "a/deleted/file/that/is/a/copy/move.me", hash, true));

    assertAppliedAndReverted(preparationResult);
  }

  @Test
  public void testMoveCriticalFileByContent() throws Exception {
    myPatchSpec.setStrict(true);
    myPatchSpec.setCriticalFiles(Collections.singletonList("a/deleted/file/that/is/a/copy/move.me"));
    FileUtil.writeToFile(new File(myOlderDir, "move/from/this/directory/move.me"), "old_content");
    FileUtil.writeToFile(new File(myOlderDir, "a/deleted/file/that/is/a/copy/move.me"), "new_content");
    FileUtil.writeToFile(new File(myNewerDir, "move/to/this/directory/move.me"), "new_content");
    createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(findAction(preparationResult.patch, "move/to/this/directory/move.me"))
      .isInstanceOf(CreateAction.class);

    assertAppliedAndReverted(preparationResult);
  }

  @Test
  public void testDontMoveFromDirectoryToFile() throws Exception {
    myPatchSpec.setStrict(true);
    FileUtil.createDirectory(new File(myOlderDir, "from/move.me"));
    FileUtil.writeToFile(new File(myNewerDir, "move/to/move.me"), "different");
    createPatch();

    // creating a patch would have crashed if the directory had been chosen
    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).isEmpty();
    assertThat(findAction(preparationResult.patch, "move/to/move.me")).isInstanceOf(CreateAction.class);
    assertThat(findAction(preparationResult.patch, "from/move.me/")).isInstanceOf(DeleteAction.class);

    assertAppliedAndReverted(preparationResult);
  }

  @Test
  public void testMoveFileByLocation() throws Exception {
    myPatchSpec.setStrict(true);
    FileUtil.writeToFile(new File(myOlderDir, "move/from/this/directory/move.me"), "they");
    FileUtil.writeToFile(new File(myOlderDir, "not/from/this/one/move.me"), "are");
    FileUtil.writeToFile(new File(myNewerDir, "move/to/this/directory/move.me"), "different");
    createPatch();

    long hash = Digester.digestStream(new ByteArrayInputStream("they".getBytes(StandardCharsets.UTF_8)));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(findAction(preparationResult.patch, "move/to/this/directory/move.me"))
      .isEqualTo(new UpdateAction(preparationResult.patch, "move/to/this/directory/move.me", "move/from/this/directory/move.me", hash, false));

    assertAppliedAndReverted(preparationResult);
  }

  @Test
  public void testSymlinkAdded() throws Exception {
    assumeSymLinkCreationIsSupported();

    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.link"));

    assertAppliedAndReverted();
  }

  @Test
  public void testSymlinkRemoved() throws Exception {
    assumeSymLinkCreationIsSupported();

    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));

    assertAppliedAndReverted();
  }

  @Test
  public void testSymlinkRenamed() throws Exception {
    assumeSymLinkCreationIsSupported();

    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));
    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.lnk"));

    assertAppliedAndReverted();
  }

  @Test
  public void testSymlinkRetargeted() throws Exception {
    assumeSymLinkCreationIsSupported();

    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));
    Utils.createLink("./Readme.txt", new File(myNewerDir, "Readme.link"));

    assertAppliedAndReverted();
  }

  @Test
  public void testZipFileMove() throws Exception {
    resetNewerDir();
    FileUtil.rename(new File(myNewerDir, "lib/annotations.jar"), new File(myNewerDir, "lib/redist/annotations.jar"));

    assertAppliedAndReverted();
  }

  @Test
  public void testZipFileMoveWithUpdate() throws Exception {
    resetNewerDir();
    FileUtil.delete(new File(myNewerDir, "lib/annotations.jar"));
    FileUtil.copy(new File(dataDir, "lib/annotations_changed.jar"), new File(myNewerDir, "lib/redist/annotations.jar"));

    assertAppliedAndReverted();
  }

  @Test
  public void testReadOnlyFilesAreDeletable() throws Exception {
    File file = new File(myOlderDir, "bin/read_only_to_delete");
    FileUtil.writeToFile(file, "bye");
    assertTrue(file.setWritable(false, false));

    createPatch();
    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndReverted(preparationResult);
  }

  @Test
  public void testExecutableFlagChange() throws Exception {
    assumeFalse("Windows-allergic", Utils.IS_WINDOWS);

    FileUtil.writeToFile(new File(myOlderDir, "bin/to_become_executable"), "to_become_executable");
    FileUtil.writeToFile(new File(myOlderDir, "bin/to_become_plain"), "to_become_plain");
    Utils.setExecutable(new File(myOlderDir, "bin/to_become_plain"), true);
    resetNewerDir();
    Utils.setExecutable(new File(myNewerDir, "bin/to_become_plain"), false);
    Utils.setExecutable(new File(myNewerDir, "bin/to_become_executable"), true);

    assertAppliedAndReverted();
  }

  @Test
  public void multipleDirectorySymlinks() throws Exception {
    assumeSymLinkCreationIsSupported();

    resetNewerDir();

    randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Libraries/lib1.dylib"));
    randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Libraries/lib2.dylib"));
    randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Resources/r1.bin"));
    randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Resources/r2.bin"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Versions/Current"), Paths.get("A"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Libraries"), Paths.get("Versions/Current/Libraries"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Resources"), Paths.get("Versions/Current/Resources"));
    Files.createDirectories(myOlderDir.toPath().resolve("Home/Frameworks"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("Home/Frameworks/A.framework"), Paths.get("../../A.framework"));

    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Libraries/lib1.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Libraries/lib2.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Resources/r1.bin"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Resources/r2.bin"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/B/Libraries/lib1.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/B/Libraries/lib2.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/B/Resources/r1.bin"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/B/Resources/r2.bin"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Versions/Previous"), Paths.get("A"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Versions/Current"), Paths.get("B"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Libraries"), Paths.get("Versions/Current/Libraries"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Resources"), Paths.get("Versions/Current/Resources"));

    assertAppliedAndReverted();
  }

  @Test
  public void symlinksToFilesAndDirectories() throws Exception {
    assumeSymLinkCreationIsSupported();

    resetNewerDir();

    randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Libraries/lib1.dylib"));
    randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Libraries/lib2.dylib"));
    randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Resources/r1/res.bin"));
    randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Resources/r2/res.bin"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Versions/Current"), Paths.get("A"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Libraries"), Paths.get("Versions/Current/Libraries"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Resources"), Paths.get("Versions/Current/Resources"));

    randomFile(myNewerDir.toPath().resolve("A.framework/Libraries/lib1.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Libraries/lib2.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Resources/r1/res.bin"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Resources/r2/res.bin"));

    assertAppliedAndReverted();
  }

  @Override
  protected Patch createPatch() throws IOException {
    assertFalse(myFile.exists());
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);
    assertTrue(myFile.exists());
    return patch;
  }

  private static PatchAction findAction(Patch patch, String path) {
    return ContainerUtil.find(patch.getActions(), a -> a.getPath().equals(path));
  }

  private static void modifyFile(File file) throws IOException {
    try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
      raf.seek(20);
      raf.write(42);
    }
  }

  private void assertNotApplied(PatchFileCreator.PreparationResult preparationResult) throws Exception {
    assertNotApplied(preparationResult, Collections.emptyMap());
  }

  private void assertNotApplied(PatchFileCreator.PreparationResult preparationResult,
                                Map<String, ValidationResult.Option> options) throws Exception {
    Patch patch = preparationResult.patch;
    File backupDir = myDoBackup ? getTempFile("backup") : null;
    Map<String, Long> original = digest(patch, myOlderDir);

    PatchFileCreator.ApplicationResult applicationResult = PatchFileCreator.apply(preparationResult, options, backupDir, TEST_UI);
    assertFalse(applicationResult.applied);

    if (myDoBackup) {
      PatchFileCreator.revert(preparationResult, applicationResult.appliedActions, backupDir, TEST_UI);
      assertEquals(original, digest(patch, myOlderDir));
    }
  }

  private void assertAppliedAndReverted() throws Exception {
    if (!myFile.exists()) {
      createPatch();
    }
    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertAppliedAndReverted(preparationResult, expected -> {});
  }

  private void assertAppliedAndReverted(PatchFileCreator.PreparationResult preparationResult) throws Exception {
    assertAppliedAndReverted(preparationResult, expected -> {});
  }

  private void assertAppliedAndReverted(PatchFileCreator.PreparationResult preparationResult,
                                        Consumer<Map<String, Long>> corrector) throws Exception {
    Patch patch = preparationResult.patch;
    Map<String, Long> original = digest(patch, myOlderDir);
    Map<String, Long> target = digest(patch, myNewerDir);
    corrector.accept(target);
    File backupDir = myDoBackup ? getTempFile("backup") : null;

    Map<String, ValidationResult.Option> options = new HashMap<>();
    for (ValidationResult each : preparationResult.validationResults) {
      if (patch.isStrict()) {
        assertThat(each.options).isNotEmpty().doesNotContain(ValidationResult.Option.NONE);
        options.put(each.path, each.options.get(0));
      }
      else {
        assertNotSame(each.toString(), ValidationResult.Kind.ERROR, each.kind);
      }
    }

    PatchFileCreator.ApplicationResult applicationResult = PatchFileCreator.apply(preparationResult, options, backupDir, TEST_UI);
    if (applicationResult.error != null) {
      throw new AssertionError("patch failed", applicationResult.error);
    }
    assertTrue(applicationResult.applied);
    assertEquals(target, digest(patch, myOlderDir));

    if (myDoBackup) {
      PatchFileCreator.revert(preparationResult, applicationResult.appliedActions, backupDir, TEST_UI);
      assertEquals(original, digest(patch, myOlderDir));
    }
  }

  private static class MyFailOnApplyPatchAction extends PatchAction {
    MyFailOnApplyPatchAction(Patch patch) {
      super(patch, "_dummy_file_", Digester.INVALID);
    }

    @Override
    protected boolean isModified(File toFile) {
      return false;
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