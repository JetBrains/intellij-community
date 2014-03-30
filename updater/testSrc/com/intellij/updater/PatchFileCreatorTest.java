package com.intellij.updater;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class PatchFileCreatorTest extends PatchTestCase {
  private File myFile;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    myFile = getTempFile("patch.zip");
  }

  @Test
  public void testCreatingAndApplying() throws Exception {
    createPatch();

    assertAppliedAndRevertedCorrectly(PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void failOnEmptySourceJar() throws Exception {
    final File sourceJar = new File(myOlderDir, "lib/empty.jar");
    if (sourceJar.exists()) sourceJar.delete();
    assertTrue(sourceJar.createNewFile());

    try {
      final File targetJar = new File(myNewerDir, "lib/empty.jar");
      FileUtil.copy(new File(myNewerDir, "lib/annotations.jar"), targetJar);

      try {
        createPatch();
        fail("Should have failed to create a patch from empty .jar");
      }
      catch (IOException e) {
        final String reason = e.getMessage();
        assertEquals("Corrupted source file: " + sourceJar, reason);
      }
      finally {
        targetJar.delete();
      }
    }
    finally {
      sourceJar.delete();
    }
  }

  @Test
  public void failOnEmptyTargetJar() throws Exception {
    final File sourceJar = new File(myOlderDir, "lib/empty.jar");
    FileUtil.copy(new File(myOlderDir, "lib/annotations.jar"), sourceJar);

    try {
      final File targetJar = new File(myNewerDir, "lib/empty.jar");
      if (targetJar.exists()) targetJar.delete();
      assertTrue(targetJar.createNewFile());

      try {
        createPatch();
        fail("Should have failed to create a patch against empty .jar");
      }
      catch (IOException e) {
        final String reason = e.getMessage();
        assertEquals("Corrupted target file: " + targetJar, reason);
      }
      finally {
        targetJar.delete();
      }
    }
    finally {
      sourceJar.delete();
    }
  }

  @Test
  public void testReverting() throws Exception {
    createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    preparationResult.patch.getActions().add(new MyFailOnApplyPatchAction());
    assertNothingHasChanged(preparationResult, new HashMap<String, ValidationResult.Option>());
  }

  @Test
  public void testApplyingWithAbsentFileToDelete() throws Exception {
    PatchFileCreator.create(myOlderDir, myNewerDir, myFile, Collections.<String>emptyList(), Collections.<String>emptyList(),
                            Collections.<String>emptyList(), TEST_UI);

    new File(myOlderDir, "bin/idea.bat").delete();

    assertAppliedAndRevertedCorrectly(PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testApplyingWithAbsentOptionalFile() throws Exception {
    FileUtil.writeToFile(new File(myNewerDir, "bin/idea.bat"), "new content".getBytes());

    PatchFileCreator.create(myOlderDir, myNewerDir, myFile, Collections.<String>emptyList(), Collections.<String>emptyList(),
                            Collections.singletonList("bin/idea.bat"), TEST_UI);

    new File(myOlderDir, "bin/idea.bat").delete();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    assertTrue(preparationResult.validationResults.isEmpty());
    assertAppliedAndRevertedCorrectly(preparationResult);
  }

  @Test
  public void testRevertingWithAbsentFileToDelete() throws Exception {
    PatchFileCreator.create(myOlderDir, myNewerDir, myFile, Collections.<String>emptyList(), Collections.<String>emptyList(),
                            Collections.<String>emptyList(), TEST_UI);

    new File(myOlderDir, "bin/idea.bat").delete();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    preparationResult.patch.getActions().add(new MyFailOnApplyPatchAction());
    assertNothingHasChanged(preparationResult, new HashMap<String, ValidationResult.Option>());
  }

  @Test
  public void testApplyingWithoutCriticalFiles() throws Exception {
    PatchFileCreator.create(myOlderDir, myNewerDir, myFile, Collections.<String>emptyList(), Collections.<String>emptyList(),
                            Collections.<String>emptyList(), TEST_UI);
    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);

    assertTrue(PatchFileCreator.apply(preparationResult, new HashMap<String, ValidationResult.Option>(), TEST_UI));
  }

  @Test
  public void testApplyingWithCriticalFiles() throws Exception {
    PatchFileCreator.create(myOlderDir, myNewerDir, myFile, Collections.<String>emptyList(), Arrays.asList("lib/annotations.jar"),
                            Collections.<String>emptyList(), TEST_UI);

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);

    assertTrue(PatchFileCreator.apply(preparationResult, new HashMap<String, ValidationResult.Option>(), TEST_UI));
    assertAppliedCorrectly();
  }

  @Test
  public void testApplyingWithCaseChangedNames() throws Exception {
    FileUtil.rename(new File(myOlderDir, "Readme.txt"),
                    new File(myOlderDir, "README.txt"));

    PatchFileCreator.create(myOlderDir, myNewerDir, myFile, Collections.<String>emptyList(), Collections.<String>emptyList(),
                            Collections.<String>emptyList(), TEST_UI);

    assertAppliedAndRevertedCorrectly(PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testCreatingAndApplyingWhenDirectoryBecomesFile() throws Exception {
    File file = new File(myOlderDir, "Readme.txt");
    file.delete();
    file.mkdirs();

    new File(file, "subFile.txt").createNewFile();
    new File(file, "subDir").mkdir();
    new File(file, "subDir/subFile.txt").createNewFile();

    FileUtil.copy(new File(myOlderDir, "lib/boot.jar"),
                  new File(myOlderDir, "lib/boot_with_directory_becomes_file.jar"));

    PatchFileCreator.create(myOlderDir, myNewerDir, myFile, Collections.<String>emptyList(), Collections.<String>emptyList(),
                            Collections.<String>emptyList(), TEST_UI);

    assertAppliedAndRevertedCorrectly(PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testCreatingAndApplyingWhenFileBecomesDirectory() throws Exception {
    File file = new File(myOlderDir, "bin");
    assertTrue(FileUtil.delete(file));
    file.createNewFile();

    FileUtil.copy(new File(myOlderDir, "lib/boot_with_directory_becomes_file.jar"),
                  new File(myOlderDir, "lib/boot.jar"));

    PatchFileCreator.create(myOlderDir, myNewerDir, myFile, Collections.<String>emptyList(), Collections.<String>emptyList(),
                            Collections.<String>emptyList(), TEST_UI);

    assertAppliedAndRevertedCorrectly(PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI));
  }

  @Test
  public void testConsideringOptions() throws Exception {
    createPatch();

    PatchFileCreator.PreparationResult preparationResult = PatchFileCreator.prepareAndValidate(myFile, myOlderDir, TEST_UI);
    Map<String, ValidationResult.Option> options = new HashMap<String, ValidationResult.Option>();
    for (PatchAction each : preparationResult.patch.getActions()) {
      options.put(each.getPath(), ValidationResult.Option.IGNORE);
    }

    assertNothingHasChanged(preparationResult, options);
  }

  private void createPatch() throws IOException, OperationCancelledException {
    PatchFileCreator.create(myOlderDir, myNewerDir, myFile,
                            Collections.<String>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList(), TEST_UI);
    assertTrue(myFile.exists());
  }

  private void assertNothingHasChanged(PatchFileCreator.PreparationResult preparationResult, Map<String, ValidationResult.Option> options)
    throws Exception {
    Map<String, Long> before = Digester.digestFiles(myOlderDir, Collections.<String>emptyList(), TEST_UI);
    PatchFileCreator.apply(preparationResult, options, TEST_UI);
    Map<String, Long> after = Digester.digestFiles(myOlderDir, Collections.<String>emptyList(), TEST_UI);

    DiffCalculator.Result diff = DiffCalculator.calculate(before, after);
    assertTrue(diff.filesToCreate.isEmpty());
    assertTrue(diff.filesToDelete.isEmpty());
    assertTrue(diff.filesToUpdate.isEmpty());
  }

  private void assertAppliedAndRevertedCorrectly(PatchFileCreator.PreparationResult preparationResult)
    throws IOException, OperationCancelledException {

    Map<String, Long> original = Digester.digestFiles(myOlderDir, Collections.<String>emptyList(), TEST_UI);

    File backup = getTempFile("backup");

    for (ValidationResult each : preparationResult.validationResults) {
      assertTrue(each.toString(), each.kind != ValidationResult.Kind.ERROR);
    }

    List<PatchAction> appliedActions =
      PatchFileCreator.apply(preparationResult, new HashMap<String, ValidationResult.Option>(), backup, TEST_UI).appliedActions;
    assertAppliedCorrectly();

    assertFalse(original.equals(Digester.digestFiles(myOlderDir, Collections.<String>emptyList(), TEST_UI)));

    PatchFileCreator.revert(preparationResult, appliedActions, backup, TEST_UI);

    assertEquals(original, Digester.digestFiles(myOlderDir, Collections.<String>emptyList(), TEST_UI));
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
    ZipFile zipFile = new ZipFile(new File(myOlderDir, jar));
    try {
      ZipEntry entry = zipFile.getEntry(entryName);
      assertNotNull(entry);
      assertEquals(expectedSize, entry.getSize());
    }
    finally {
      zipFile.close();
    }
  }

  private void checkNoZipEntry(String jar, String entryName) throws IOException {
    ZipFile zipFile = new ZipFile(new File(myOlderDir, jar));
    try {
      assertNull(zipFile.getEntry(entryName));
    }
    finally {
      zipFile.close();
    }
  }

  private static class MyFailOnApplyPatchAction extends PatchAction {
    public MyFailOnApplyPatchAction() {
      super("_dummy_file_", -1);
    }

    @Override
    protected void doBuildPatchFile(File olderFile, File newerFile, ZipOutputStream patchOutput) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    protected ValidationResult doValidate(File toFile) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void doApply(ZipFile patchFile, File toFile) throws IOException {
      throw new IOException("dummy exception");
    }

    @Override
    protected void doBackup(File toFile, File backupFile) throws IOException {
    }

    @Override
    protected void doRevert(File toFile, File backupFile) throws IOException {
    }
  }
}
