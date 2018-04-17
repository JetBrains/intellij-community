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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public abstract class PatchApplyingRevertingTest extends PatchTestCase {
  private File myFile;
  protected PatchSpec myPatchSpec;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFile = getTempFile("patch.zip");
    myPatchSpec = new PatchSpec()
      .setOldFolder(myOlderDir.getAbsolutePath())
      .setNewFolder(myNewerDir.getAbsolutePath());
  }

  @Test
  public void testCreatingAndApplying() throws Exception {
    assertAppliedAndReverted();
  }

  @Test
  public void testCreatingAndApplyingWithoutBackup() throws Exception {
    createPatch();
    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    Map<String, Long> expected = digest(preparationResult.patch, myNewerDir);

    PatchFileCreator.ApplicationResult applicationResult = PatchFileCreator.apply(preparationResult, Collections.emptyMap(), null, TEST_UI);
    assertTrue(applicationResult.applied);
    assertEquals(expected, digest(preparationResult.patch, myOlderDir));
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
    assumeTrue(UtilsTest.IS_WINDOWS);
    doLockedFileTest();
  }

  @Test
  public void testRevertedWhenFileToUpdateIsLocked() throws Exception {
    assumeTrue(UtilsTest.IS_WINDOWS);
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

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(findAction(preparationResult.patch, "move/to/this/directory/move.me"))
      .isInstanceOf(UpdateAction.class)
      .hasFieldOrPropertyWithValue("move", true)
      .hasFieldOrPropertyWithValue("sourcePath", "a/deleted/file/that/is/a/copy/move.me");

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

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(findAction(preparationResult.patch, "move/to/this/directory/move.me"))
      .isInstanceOf(UpdateAction.class)
      .hasFieldOrPropertyWithValue("move", false)
      .hasFieldOrPropertyWithValue("sourcePath", "move/from/this/directory/move.me");

    assertAppliedAndReverted(preparationResult);
  }

  @Test
  public void testSymlinkAdded() throws Exception {
    assumeTrue(!UtilsTest.IS_WINDOWS);

    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.link"));

    assertAppliedAndReverted();
  }

  @Test
  public void testSymlinkRemoved() throws Exception {
    assumeTrue(!UtilsTest.IS_WINDOWS);

    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));

    assertAppliedAndReverted();
  }

  @Test
  public void testSymlinkRenamed() throws Exception {
    assumeTrue(!UtilsTest.IS_WINDOWS);

    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));
    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.lnk"));

    assertAppliedAndReverted();
  }

  @Test
  public void testSymlinkRetargeted() throws Exception {
    assumeTrue(!UtilsTest.IS_WINDOWS);

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
  public void testDoNotLeaveEmptyDirectories() throws Exception {
    FileUtil.createDirectory(new File(myNewerDir, "new_empty_dir/sub_dir"));
    createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertAppliedAndReverted(preparationResult, expected -> {
      expected.remove("new_empty_dir/");
      expected.remove("new_empty_dir/sub_dir/");
    });
  }

  @Test
  public void testUpdatingMissingOptionalDirectory() throws Exception {
    FileUtil.copy(new File(myOlderDir, "bin/idea.bat"), new File(myOlderDir, "jre/bin/java"));
    FileUtil.copy(new File(myOlderDir, "lib/annotations.jar"), new File(myOlderDir, "jre/lib/rt.jar"));
    FileUtil.copy(new File(myOlderDir, "lib/boot.jar"), new File(myOlderDir, "jre/lib/tools.jar"));
    resetNewerDir();
    FileUtil.rename(new File(myNewerDir, "jre"), new File(myNewerDir, "jre32"));
    FileUtil.writeToFile(new File(myNewerDir, "jre32/lib/font-config.bfc"), "# empty");

    myPatchSpec.setOptionalFiles(Arrays.asList(
      "jre/bin/java", "jre/bin/jvm.dll", "jre/lib/rt.jar", "jre/lib/tools.jar",
      "jre32/bin/java", "jre32/bin/jvm.dll", "jre32/lib/rt.jar", "jre32/lib/tools.jar", "jre32/lib/font-config.bfc"));
    createPatch();

    FileUtil.delete(new File(myOlderDir, "jre"));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertAppliedAndReverted(preparationResult, expected -> {
      List<String> keys = ContainerUtil.findAll(expected.keySet(), k -> k.startsWith("jre32/"));
      keys.forEach(expected::remove);
    });
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
    File backup = getTempFile("backup");
    Map<String, Long> original = digest(patch, myOlderDir);

    PatchFileCreator.ApplicationResult applicationResult = PatchFileCreator.apply(preparationResult, options, backup, TEST_UI);
    assertFalse(applicationResult.applied);

    PatchFileCreator.revert(preparationResult, applicationResult.appliedActions, backup, TEST_UI);
    assertEquals(original, digest(patch, myOlderDir));
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
    File backup = getTempFile("backup");

    Map<String, ValidationResult.Option> options = new HashMap<>();
    for (ValidationResult each : preparationResult.validationResults) {
      if (patch.isStrict()) {
        assertThat(each.options).isNotEmpty().doesNotContain(ValidationResult.Option.NONE);
        options.put(each.path, each.options.get(0));
      }
      else {
        assertTrue(each.toString(), each.kind != ValidationResult.Kind.ERROR);
      }
    }

    PatchFileCreator.ApplicationResult applicationResult = PatchFileCreator.apply(preparationResult, options, backup, TEST_UI);
    assertTrue(applicationResult.applied);
    assertEquals(target, digest(patch, myOlderDir));

    PatchFileCreator.revert(preparationResult, applicationResult.appliedActions, backup, TEST_UI);
    assertEquals(original, digest(patch, myOlderDir));
  }

  private static class MyFailOnApplyPatchAction extends PatchAction {
    public MyFailOnApplyPatchAction(Patch patch) {
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

    @Override
    protected void doBackup(File toFile, File backupFile) { }

    @Override
    protected void doRevert(File toFile, File backupFile) { }
  }
}