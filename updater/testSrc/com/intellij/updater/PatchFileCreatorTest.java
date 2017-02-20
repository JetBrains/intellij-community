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
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public abstract class PatchFileCreatorTest extends PatchTestCase {
  private File myFile;
  protected PatchSpec myPatchSpec;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    myFile = getTempFile("patch.zip");
    myPatchSpec = new PatchSpec()
      .setOldFolder(myOlderDir.getAbsolutePath())
      .setNewFolder(myNewerDir.getAbsolutePath());
  }

  @Test
  public void testCreatingAndApplying() throws Exception {
    assertAppliedAndRevertedCorrectly();
  }

  @Test
  public void testCreatingAndApplyingStrict() throws Exception {
    myPatchSpec.setStrict(true);
    assertAppliedAndRevertedCorrectly();
  }

  @Test
  public void testCreatingAndApplyingOnADifferentRoot() throws Exception {
    myPatchSpec.setRoot("bin/");
    myPatchSpec.setStrict(true);

    Patch patch = createPatch();

    File target = new File(myOlderDir, "bin");
    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, target, TEST_UI));
  }

  @Test
  public void testCreatingAndFailingOnADifferentRoot() throws Exception {
    myPatchSpec.setRoot("bin/");
    myPatchSpec.setStrict(true);

    Patch patch = createPatch();

    File target = new File(myOlderDir, "bin");
    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, target, TEST_UI);
    preparationResult.patch.getActions().add(new MyFailOnApplyPatchAction(patch));
    assertNothingHasChanged(patch, preparationResult, new HashMap<>());
  }

  @Test
  public void testReverting() throws Exception {
    Patch patch = createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    preparationResult.patch.getActions().add(new MyFailOnApplyPatchAction(patch));
    assertNothingHasChanged(patch, preparationResult, new HashMap<>());
  }

  @Test
  public void testRevertedWhenFileToDeleteIsProcessLocked() throws Exception {
    assumeTrue(UtilsTest.IS_WINDOWS);

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    try (RandomAccessFile raf = new RandomAccessFile(new File(myOlderDir, "bin/idea.bat"),"rw")) {
      // Lock the file. FileLock is not good here, because we need to prevent deletion.
      int b = raf.read();
      raf.seek(0);
      raf.write(b);

      PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);

      Map<String, Long> original = patch.digestFiles(myOlderDir, Collections.emptyList(), false, TEST_UI);

      File backup = getTempFile("backup");
      PatchFileCreator.apply(preparationResult, new HashMap<>(), backup, TEST_UI);

      assertEquals(original, patch.digestFiles(myOlderDir, Collections.emptyList(), false, TEST_UI));
    }
  }

  @Test
  public void testApplyingWithAbsentFileToDelete() throws Exception {
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    FileUtil.delete(new File(myOlderDir, "bin/idea.bat"));

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testApplyingWithAbsentFileToUpdateStrict() throws Exception {
    myPatchSpec.setStrict(true);
    PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

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
    FileUtil.writeToFile(new File(myNewerDir, "bin/idea.bat"), "new content".getBytes("UTF-8"));

    myPatchSpec.setOptionalFiles(Collections.singletonList("bin/idea.bat"));
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    FileUtil.delete(new File(myOlderDir, "bin/idea.bat"));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndRevertedCorrectly(patch, preparationResult);
  }

  @Test
  public void testRevertingWithAbsentFileToDelete() throws Exception {
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    FileUtil.delete(new File(myOlderDir, "bin/idea.bat"));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    preparationResult.patch.getActions().add(new MyFailOnApplyPatchAction(patch));
    assertNothingHasChanged(patch, preparationResult, new HashMap<>());
  }

  @Test
  public void testApplyingWithoutCriticalFiles() throws Exception {
    PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);
    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);

    assertTrue(PatchFileCreator.apply(preparationResult, new HashMap<>(), TEST_UI));
  }

  @Test
  public void testApplyingWithCriticalFiles() throws Exception {
    myPatchSpec.setCriticalFiles(Collections.singletonList("lib/annotations.jar"));
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testApplyingWithModifiedCriticalFiles() throws Exception {
    myPatchSpec.setStrict(true);
    myPatchSpec.setCriticalFiles(Collections.singletonList("lib/annotations.jar"));
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    try (RandomAccessFile raf = new RandomAccessFile(new File(myOlderDir, "lib/annotations.jar"), "rw")) {
      raf.seek(20);
      raf.write(42);
    }

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testApplyingWithModifiedCriticalFilesAndDifferentRoot() throws Exception {
    myPatchSpec.setStrict(true);
    myPatchSpec.setRoot("lib/");
    myPatchSpec.setCriticalFiles(Collections.singletonList("lib/annotations.jar"));
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    try (RandomAccessFile raf = new RandomAccessFile(new File(myOlderDir, "lib/annotations.jar"), "rw")) {
      raf.seek(20);
      raf.write(42);
    }

    File toDir = new File(myOlderDir, "lib/");
    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, toDir, TEST_UI));
  }

  @Test
  public void testApplyingWithCaseChangedNames() throws Exception {
    FileUtil.rename(new File(myOlderDir, "Readme.txt"), new File(myOlderDir, "README.txt"));

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testCreatingAndApplyingWhenDirectoryBecomesFile() throws Exception {
    File file = new File(myOlderDir, "Readme.txt");
    FileUtil.delete(file);
    FileUtil.createDirectory(file);

    FileUtil.writeToFile(new File(file, "subFile.txt"), "");
    FileUtil.writeToFile(new File(file, "subDir/subFile.txt"), "");

    FileUtil.copy(new File(myOlderDir, "lib/boot.jar"), new File(myOlderDir, "lib/boot_with_directory_becomes_file.jar"));

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testCreatingAndApplyingWhenFileBecomesDirectory() throws Exception {
    File file = new File(myOlderDir, "bin");
    FileUtil.delete(file);
    FileUtil.writeToFile(file, "");

    FileUtil.copy(new File(myOlderDir, "lib/boot_with_directory_becomes_file.jar"), new File(myOlderDir, "lib/boot.jar"));

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testConsideringOptions() throws Exception {
    Patch patch = createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    Map<String, ValidationResult.Option> options = new HashMap<>();
    for (PatchAction each : preparationResult.patch.getActions()) {
      options.put(each.getPath(), ValidationResult.Option.IGNORE);
    }

    assertNothingHasChanged(patch, preparationResult, options);
  }

  @Test
  public void testApplyWhenCommonFileChanges() throws Exception {
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    FileUtil.copy(new File(myOlderDir, "/lib/bootstrap.jar"),
                  new File(myOlderDir, "/lib/boot.jar"));

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndRevertedCorrectly(patch, preparationResult);
  }

  @Test
  public void testApplyWhenCommonFileChangesStrict() throws Exception {
    myPatchSpec.setStrict(true);
    PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    FileUtil.copy(new File(myOlderDir, "/lib/bootstrap.jar"), new File(myOlderDir, "/lib/boot.jar"));

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
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    FileUtil.writeToFile(new File(myOlderDir, "new_file.txt"), "hello");

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndRevertedCorrectly(patch, preparationResult);
  }

  @Test
  public void testApplyWhenNewFileExistsStrict() throws Exception {
    myPatchSpec.setStrict(true);
    myPatchSpec.setDeleteFiles(Collections.singletonList("lib/java_pid.*\\.hprof"));

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    FileUtil.writeToFile(new File(myOlderDir, "new_file.txt"), "hello");
    FileUtil.writeToFile(new File(myOlderDir, "lib/java_pid1234.hprof"), "bye!");

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).containsExactly(
      new ValidationResult(ValidationResult.Kind.CONFLICT,
                           "new_file.txt",
                           ValidationResult.Action.VALIDATE,
                           "Unexpected file",
                           ValidationResult.Option.DELETE));
    assertAppliedAndRevertedCorrectly(patch, preparationResult);
  }

  @Test
  public void testApplyWhenNewDeletableFileExistsStrict() throws Exception {
    myPatchSpec.setStrict(true);
    myPatchSpec.setDeleteFiles(Collections.singletonList("lib/java_pid.*\\.hprof"));

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

    FileUtil.writeToFile(new File(myOlderDir, "lib/java_pid1234.hprof"), "bye!");

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndRevertedCorrectly(patch, preparationResult);
  }

  @Test
  public void testApplyWhenNewDirectoryExistsStrict() throws Exception {
    myPatchSpec.setStrict(true);
    FileUtil.writeToFile(new File(myOlderDir, "delete/delete_me.txt"), "bye!");

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);

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
    assertAppliedAndRevertedCorrectly(patch, preparationResult);
  }

  @Test
  public void testMoveFileByContent() throws Exception {
    myPatchSpec.setStrict(true);
    FileUtil.writeToFile(new File(myOlderDir, "move/from/this/directory/move.me"), "old_content");
    FileUtil.writeToFile(new File(myOlderDir, "a/deleted/file/that/is/a/copy/move.me"), "new_content");
    FileUtil.writeToFile(new File(myNewerDir, "move/to/this/directory/move.me"), "new_content");

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);
    PatchAction action = getAction(patch, "move/to/this/directory/move.me");
    assertTrue(action instanceof UpdateAction);
    UpdateAction update = (UpdateAction)action;
    assertTrue(update.isMove());
    assertEquals("a/deleted/file/that/is/a/copy/move.me", update.getSourcePath());

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testMoveCriticalFileByContent() throws Exception {
    myPatchSpec.setStrict(true);
    myPatchSpec.setCriticalFiles(Collections.singletonList("a/deleted/file/that/is/a/copy/move.me"));

    FileUtil.writeToFile(new File(myOlderDir, "move/from/this/directory/move.me"), "old_content");
    FileUtil.writeToFile(new File(myOlderDir, "a/deleted/file/that/is/a/copy/move.me"), "new_content");
    FileUtil.writeToFile(new File(myNewerDir, "move/to/this/directory/move.me"), "new_content");

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);
    PatchAction action = getAction(patch, "move/to/this/directory/move.me");
    assertTrue(action instanceof CreateAction);

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testDontMoveFromDirectoryToFile() throws Exception {
    myPatchSpec.setStrict(true);
    FileUtil.createDirectory(new File(myOlderDir, "from/move.me"));
    FileUtil.writeToFile(new File(myNewerDir, "move/to/move.me"), "different");

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);
    // Creating a patch would have crashed if the directory had been chosen.
    PatchAction action = getAction(patch, "move/to/move.me");
    assertTrue(action instanceof CreateAction);
    action = getAction(patch, "from/move.me/");
    assertTrue(action instanceof DeleteAction);
    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertThat(preparationResult.validationResults).isEmpty();
    assertAppliedAndRevertedCorrectly(patch, preparationResult);
  }

  @Test
  public void testMoveFileByLocation() throws Exception {
    myPatchSpec.setStrict(true);
    FileUtil.writeToFile(new File(myOlderDir, "move/from/this/directory/move.me"), "they");
    FileUtil.writeToFile(new File(myOlderDir, "not/from/this/one/move.me"), "are");
    FileUtil.writeToFile(new File(myNewerDir, "move/to/this/directory/move.me"), "different");

    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);
    PatchAction action = getAction(patch, "move/to/this/directory/move.me");
    assertTrue(action instanceof UpdateAction);
    UpdateAction update = (UpdateAction)action;
    assertTrue(!update.isMove());
    assertEquals("move/from/this/directory/move.me", update.getSourcePath());

    assertAppliedAndRevertedCorrectly(patch, PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testSymlinkAdded() throws Exception {
    assumeTrue(!UtilsTest.IS_WINDOWS);

    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.link"));

    assertAppliedAndRevertedCorrectly();
  }

  @Test
  public void testSymlinkRemoved() throws Exception {
    assumeTrue(!UtilsTest.IS_WINDOWS);

    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));

    assertAppliedAndRevertedCorrectly();
  }

  @Test
  public void testSymlinkRenamed() throws Exception {
    assumeTrue(!UtilsTest.IS_WINDOWS);

    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));
    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.lnk"));

    assertAppliedAndRevertedCorrectly();
  }

  @Test
  public void testSymlinkRetargeted() throws Exception {
    assumeTrue(!UtilsTest.IS_WINDOWS);

    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));
    Utils.createLink("./Readme.txt", new File(myNewerDir, "Readme.link"));

    assertAppliedAndRevertedCorrectly();
  }

  private void assertAppliedAndRevertedCorrectly() throws Exception {
    assertAppliedAndRevertedCorrectly(createPatch(), PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  protected PatchAction getAction(Patch patch, String path) {
    return ContainerUtil.find(patch.getActions(), a -> a.getPath().equals(path));
  }

  protected Patch createPatch() throws IOException, OperationCancelledException {
    Patch patch = PatchFileCreator.create(myPatchSpec, myFile, TEST_UI);
    assertTrue(myFile.exists());
    return patch;
  }

  private void assertNothingHasChanged(Patch patch,
                                       PatchFileCreator.PreparationResult preparationResult,
                                       Map<String, ValidationResult.Option> options) throws Exception {
    Map<String, Long> before = patch.digestFiles(myOlderDir, Collections.emptyList(), false, TEST_UI);
    PatchFileCreator.apply(preparationResult, options, TEST_UI);
    Map<String, Long> after = patch.digestFiles(myOlderDir, Collections.emptyList(), false, TEST_UI);

    DiffCalculator.Result diff = DiffCalculator.calculate(before, after, new LinkedList<>(), false);
    assertTrue(diff.filesToCreate.isEmpty());
    assertTrue(diff.filesToDelete.isEmpty());
    assertTrue(diff.filesToUpdate.isEmpty());
  }

  private void assertAppliedAndRevertedCorrectly(Patch patch, PatchFileCreator.PreparationResult preparationResult) throws Exception {
    Map<String, Long> original = patch.digestFiles(myOlderDir, Collections.emptyList(), false, TEST_UI);
    Map<String, Long> target = patch.digestFiles(myNewerDir, Collections.emptyList(), false, TEST_UI);
    File backup = getTempFile("backup");

    Map<String, ValidationResult.Option> options = new HashMap<>();
    for (ValidationResult each : preparationResult.validationResults) {
      if (patch.isStrict()) {
        assertFalse(each.options.contains(ValidationResult.Option.NONE));
        assertTrue(each.options.size() > 0);
        options.put(each.path, each.options.get(0));
      }
      else {
        assertTrue(each.toString(), each.kind != ValidationResult.Kind.ERROR);
      }
    }

    List<PatchAction> appliedActions = PatchFileCreator.apply(preparationResult, options, backup, TEST_UI).appliedActions;
    Map<String, Long> patched = patch.digestFiles(myOlderDir, Collections.emptyList(), false, TEST_UI);

    if (patch.isStrict()) {
      assertEquals(patched, target);
    }
    else {
      assertAppliedCorrectly();
    }

    assertNotEquals(original, patched);

    PatchFileCreator.revert(preparationResult, appliedActions, backup, TEST_UI);
    Map<String, Long> reverted = patch.digestFiles(myOlderDir, Collections.emptyList(), false, TEST_UI);
    assertEquals(original, reverted);
  }

  protected void assertAppliedCorrectly() throws IOException {
    File newFile = new File(myOlderDir, "newDir/newFile.txt");
    assertTrue(newFile.exists());
    assertEquals("hello", FileUtil.loadFile(newFile));

    File changedFile = new File(myOlderDir, "Readme.txt");
    assertTrue(changedFile.exists());
    assertEquals("hello", FileUtil.loadFile(changedFile));

    assertFalse(new File(myOlderDir, "bin/idea.bat").exists());

    // do not remove unchanged
    checkZipEntry("lib/annotations.jar", "org/jetbrains/annotations/Nls.class", 502);
    // add new
    checkZipEntry("lib/annotations.jar", "org/jetbrains/annotations/NewClass.class", 453);
    // update changed
    checkZipEntry("lib/annotations.jar", "org/jetbrains/annotations/Nullable.class", 546);
    // remove obsolete
    checkNoZipEntry("lib/annotations.jar", "org/jetbrains/annotations/TestOnly.class");

    // test for archives with only deleted files
    checkNoZipEntry("lib/bootstrap.jar", "com/intellij/ide/ClassloaderUtil.class");

    // packing directories too
    checkZipEntry("lib/annotations.jar", "org/", 0);
    checkZipEntry("lib/annotations.jar", "org/jetbrains/", 0);
    checkZipEntry("lib/annotations.jar", "org/jetbrains/annotations/", 0);
    checkZipEntry("lib/bootstrap.jar", "com/", 0);
    checkZipEntry("lib/bootstrap.jar", "com/intellij/", 0);
    checkZipEntry("lib/bootstrap.jar", "com/intellij/ide/", 0);
  }

  private void checkZipEntry(String jar, String entryName, int expectedSize) throws IOException {
    try (ZipFile zipFile = new ZipFile(new File(myOlderDir, jar))) {
      ZipEntry entry = zipFile.getEntry(entryName);
      assertNotNull(entry);
      assertEquals(expectedSize, entry.getSize());
    }
  }

  private void checkNoZipEntry(String jar, String entryName) throws IOException {
    try (ZipFile zipFile = new ZipFile(new File(myOlderDir, jar))) {
      assertNull(zipFile.getEntry(entryName));
    }
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