// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.sun.jna.platform.win32.Kernel32;
import org.junit.Test;

import java.io.*;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.io.IoTestUtil.assumeNioSymLinkCreationIsSupported;
import static com.intellij.openapi.util.io.IoTestUtil.assumeSymLinkCreationIsSupported;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class PatchCreationTest extends PatchTestCase {
  @Test
  public void testDigestFiles() throws Exception {
    Patch patch = createPatch();
    Map<String, Long> checkSums = digest(patch, myOlderDir);
    assertThat(checkSums).hasSize(11);
  }

  @Test
  public void testBasics() throws Exception {
    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "bin/idea.bat", CHECKSUMS.IDEA_BAT),
      new CreateAction(patch, "newDir/"),
      new CreateAction(patch, "newDir/newFile.txt"),
      new UpdateAction(patch, "Readme.txt", CHECKSUMS.README_TXT),
      new UpdateZipAction(patch, "lib/annotations.jar",
                          singletonList("org/jetbrains/annotations/NewClass.class"),
                          singletonList("org/jetbrains/annotations/Nullable.class"),
                          singletonList("org/jetbrains/annotations/TestOnly.class"),
                          CHECKSUMS.ANNOTATIONS_JAR),
      new UpdateZipAction(patch, "lib/bootstrap.jar",
                          Collections.emptyList(),
                          Collections.emptyList(),
                          singletonList("com/intellij/ide/ClassloaderUtil.class"),
                          CHECKSUMS.BOOTSTRAP_JAR));
  }

  @Test
  public void testCreatingWithIgnoredFiles() throws Exception {
    PatchSpec spec = new PatchSpec()
      .setOldFolder(myOlderDir.getAbsolutePath())
      .setNewFolder(myNewerDir.getAbsolutePath())
      .setIgnoredFiles(asList("Readme.txt", "bin/idea.bat"));
    Patch patch = new Patch(spec, TEST_UI);

    assertThat(sortActions(patch.getActions())).containsExactly(
      new CreateAction(patch, "newDir/"),
      new CreateAction(patch, "newDir/newFile.txt"),
      new UpdateZipAction(patch, "lib/annotations.jar",
                          singletonList("org/jetbrains/annotations/NewClass.class"),
                          singletonList("org/jetbrains/annotations/Nullable.class"),
                          singletonList("org/jetbrains/annotations/TestOnly.class"),
                          CHECKSUMS.ANNOTATIONS_JAR),
      new UpdateZipAction(patch, "lib/bootstrap.jar",
                          Collections.emptyList(),
                          Collections.emptyList(),
                          singletonList("com/intellij/ide/ClassloaderUtil.class"),
                          CHECKSUMS.BOOTSTRAP_JAR));
  }

  @Test
  public void testValidation() throws Exception {
    FileUtil.delete(new File(myNewerDir, "bin/focuskiller.dll"));
    FileUtil.copy(new File(myOlderDir, "bin/focuskiller.dll"), new File(myNewerDir, "newDir/focuskiller.dll"));
    Patch patch = createPatch();

    FileUtil.writeToFile(new File(myOlderDir, "bin/idea.bat"), "changed");
    FileUtil.writeToFile(new File(myOlderDir, "bin/focuskiller.dll"), "changed");
    FileUtil.createDirectory(new File(myOlderDir, "extraDir"));
    FileUtil.writeToFile(new File(myOlderDir, "extraDir/extraFile.txt"), "");
    FileUtil.createDirectory(new File(myOlderDir, "newDir"));
    FileUtil.writeToFile(new File(myOlderDir, "newDir/newFile.txt"), "");
    FileUtil.writeToFile(new File(myOlderDir, "Readme.txt"), "changed");
    FileUtil.writeToFile(new File(myOlderDir, "lib/annotations.jar"), "changed");
    FileUtil.delete(new File(myOlderDir, "lib/bootstrap.jar"));

    assertThat(sortResults(patch.validate(myOlderDir, TEST_UI))).containsExactly(
      new ValidationResult(ValidationResult.Kind.CONFLICT,
                           "bin/focuskiller.dll",
                           ValidationResult.Action.DELETE,
                           ValidationResult.MODIFIED_MESSAGE,
                           ValidationResult.Option.DELETE, ValidationResult.Option.KEEP),
      new ValidationResult(ValidationResult.Kind.CONFLICT,
                           "bin/idea.bat",
                           ValidationResult.Action.DELETE,
                           ValidationResult.MODIFIED_MESSAGE,
                           ValidationResult.Option.DELETE, ValidationResult.Option.KEEP),
      new ValidationResult(ValidationResult.Kind.CONFLICT,
                           "newDir/",
                           ValidationResult.Action.CREATE,
                           ValidationResult.ALREADY_EXISTS_MESSAGE,
                           ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP),
      new ValidationResult(ValidationResult.Kind.CONFLICT,
                           "newDir/newFile.txt",
                           ValidationResult.Action.CREATE,
                           ValidationResult.ALREADY_EXISTS_MESSAGE,
                           ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP),
      new ValidationResult(ValidationResult.Kind.ERROR,
                           "Readme.txt",
                           ValidationResult.Action.UPDATE,
                           ValidationResult.MODIFIED_MESSAGE,
                           ValidationResult.Option.IGNORE),
      new ValidationResult(ValidationResult.Kind.ERROR,
                           "bin/focuskiller.dll",
                           ValidationResult.Action.UPDATE,
                           ValidationResult.MODIFIED_MESSAGE,
                           ValidationResult.Option.IGNORE),
      new ValidationResult(ValidationResult.Kind.ERROR,
                           "lib/annotations.jar",
                           ValidationResult.Action.UPDATE,
                           ValidationResult.MODIFIED_MESSAGE,
                           ValidationResult.Option.IGNORE),
      new ValidationResult(ValidationResult.Kind.ERROR,
                           "lib/bootstrap.jar",
                           ValidationResult.Action.UPDATE,
                           ValidationResult.ABSENT_MESSAGE,
                           ValidationResult.Option.IGNORE));
  }

  @Test
  public void testValidatingCaseOnlyRename() throws Exception {
    Patch patch = createCaseOnlyRenamePatch();
    assertThat(patch.validate(myOlderDir, TEST_UI)).isEmpty();
  }

  @Test
  public void testValidatingCaseOnlyRenameWithConflict() throws Exception {
    assertThat(Runner.isCaseSensitiveFs()).isEqualTo(SystemInfo.isFileSystemCaseSensitive);

    Patch patch = createCaseOnlyRenamePatch();
    FileUtil.writeToFile(new File(myOlderDir, "bin/IDEA.bat"), FileUtil.loadFileBytes(new File(myOlderDir, "bin/idea.bat")));

    List<ValidationResult> results = patch.validate(myOlderDir, TEST_UI);
    if (SystemInfo.isFileSystemCaseSensitive) {
      assertThat(results).containsExactly(
        new ValidationResult(ValidationResult.Kind.CONFLICT,
                             "bin/IDEA.bat",
                             ValidationResult.Action.CREATE,
                             ValidationResult.ALREADY_EXISTS_MESSAGE,
                             ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP));
    }
    else {
      assertThat(results).isEmpty();
    }
  }

  @Test
  public void testValidationWithOptionalFiles() throws Exception {
    Patch patch1 = createPatch();
    FileUtil.copy(new File(myOlderDir, "lib/boot.jar"), new File(myOlderDir, "lib/annotations.jar"));
    assertThat(patch1.validate(myOlderDir, TEST_UI)).containsExactly(
      new ValidationResult(ValidationResult.Kind.ERROR,
                           "lib/annotations.jar",
                           ValidationResult.Action.UPDATE,
                           ValidationResult.MODIFIED_MESSAGE,
                           ValidationResult.Option.IGNORE));

    PatchSpec spec = new PatchSpec()
      .setOldFolder(myOlderDir.getAbsolutePath())
      .setNewFolder(myNewerDir.getAbsolutePath())
      .setOptionalFiles(singletonList("lib/annotations.jar"));
    Patch patch2 = new Patch(spec, TEST_UI);
    FileUtil.delete(new File(myOlderDir, "lib/annotations.jar"));
    assertThat(patch2.validate(myOlderDir, TEST_UI)).isEmpty();
  }

  @Test
  public void testValidatingNonAccessibleFiles() throws Exception {
    Patch patch = createPatch();
    File f = new File(myOlderDir, "Readme.txt");
    try (FileOutputStream s = new FileOutputStream(f, true); FileLock ignored = s.getChannel().lock()) {
      String message = Utils.IS_WINDOWS ? "Locked by: [" + Kernel32.INSTANCE.GetCurrentProcessId() + "] OpenJDK Platform binary"
                                        : ValidationResult.ACCESS_DENIED_MESSAGE;
      ValidationResult.Option option = Utils.IS_WINDOWS ? ValidationResult.Option.KILL_PROCESS : ValidationResult.Option.IGNORE;
      assertThat(patch.validate(myOlderDir, TEST_UI)).containsExactly(
        new ValidationResult(ValidationResult.Kind.ERROR,
                             "Readme.txt",
                             ValidationResult.Action.UPDATE,
                             message,
                             option));
    }
  }

  @Test
  public void testZipFileMove() throws Exception {
    resetNewerDir();
    FileUtil.rename(new File(myNewerDir, "lib/annotations.jar"), new File(myNewerDir, "lib/redist/annotations.jar"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "lib/annotations.jar", CHECKSUMS.ANNOTATIONS_JAR),
      new CreateAction(patch, "lib/redist/"),
      new UpdateAction(patch, "lib/redist/annotations.jar", "lib/annotations.jar", CHECKSUMS.ANNOTATIONS_JAR, true));
  }

  @Test
  public void testZipFileMoveWithUpdate() throws Exception {
    resetNewerDir();
    FileUtil.delete(new File(myNewerDir, "lib/annotations.jar"));
    FileUtil.copy(new File(dataDir, "lib/annotations_changed.jar"), new File(myNewerDir, "lib/redist/annotations.jar"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "lib/annotations.jar", CHECKSUMS.ANNOTATIONS_JAR),
      new CreateAction(patch, "lib/redist/"),
      new UpdateZipAction(patch, "lib/redist/annotations.jar", "lib/annotations.jar",
                          singletonList("org/jetbrains/annotations/NewClass.class"),
                          singletonList("org/jetbrains/annotations/Nullable.class"),
                          singletonList("org/jetbrains/annotations/TestOnly.class"),
                          CHECKSUMS.ANNOTATIONS_JAR));
  }

  @Test
  public void testZipFileMoveWithAlternatives() throws Exception {
    FileUtil.copy(new File(myOlderDir, "lib/annotations.jar"), new File(myOlderDir, "lib64/annotations.jar"));
    resetNewerDir();
    FileUtil.rename(new File(myNewerDir, "lib/annotations.jar"), new File(myNewerDir, "lib/redist/annotations.jar"));
    FileUtil.rename(new File(myNewerDir, "lib64/annotations.jar"), new File(myNewerDir, "lib64/redist/annotations.jar"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "lib/annotations.jar", CHECKSUMS.ANNOTATIONS_JAR),
      new DeleteAction(patch, "lib64/annotations.jar", CHECKSUMS.ANNOTATIONS_JAR),
      new CreateAction(patch, "lib/redist/"),
      new CreateAction(patch, "lib64/redist/"),
      new UpdateAction(patch, "lib/redist/annotations.jar", "lib/annotations.jar", CHECKSUMS.ANNOTATIONS_JAR, true),
      new UpdateAction(patch, "lib64/redist/annotations.jar", "lib64/annotations.jar", CHECKSUMS.ANNOTATIONS_JAR, true));
  }

  @Test
  public void testNoOptionalFileMove1() throws Exception {
    resetNewerDir();
    FileUtil.copy(new File(dataDir, "lib/annotations.jar"), new File(myOlderDir, "lib/annotations.bin"));
    FileUtil.copy(new File(dataDir, "lib/annotations_changed.jar"), new File(myOlderDir, "lib64/annotations.bin"));
    FileUtil.copy(new File(dataDir, "lib/annotations.jar"), new File(myNewerDir, "lib/redist/annotations.bin"));
    FileUtil.copy(new File(dataDir, "lib/annotations.jar"), new File(myNewerDir, "lib64/redist/annotations.bin"));

    Patch patch = createPatch(spec -> spec.setOptionalFiles(asList("lib/annotations.bin", "lib/redist/annotations.bin")));
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "lib/annotations.bin", CHECKSUMS.ANNOTATIONS_JAR_BIN),
      new DeleteAction(patch, "lib64/annotations.bin", CHECKSUMS.ANNOTATIONS_CHANGED_JAR_BIN),
      new CreateAction(patch, "lib/redist/"),
      new CreateAction(patch, "lib64/redist/"),
      new UpdateAction(patch, "lib/redist/annotations.bin", "lib/annotations.bin", CHECKSUMS.ANNOTATIONS_JAR_BIN, true),
      new UpdateAction(patch, "lib64/redist/annotations.bin", "lib64/annotations.bin", CHECKSUMS.ANNOTATIONS_CHANGED_JAR_BIN, false));
  }

  @Test
  public void testNoOptionalFileMove2() throws Exception {
    resetNewerDir();
    FileUtil.copy(new File(dataDir, "lib/annotations_changed.jar"), new File(myOlderDir, "lib/annotations.bin"));
    FileUtil.copy(new File(dataDir, "lib/annotations.jar"), new File(myOlderDir, "lib64/annotations.bin"));
    FileUtil.copy(new File(dataDir, "lib/annotations.jar"), new File(myNewerDir, "lib/redist/annotations.bin"));
    FileUtil.copy(new File(dataDir, "lib/annotations.jar"), new File(myNewerDir, "lib64/redist/annotations.bin"));

    Patch patch = createPatch(spec -> spec.setOptionalFiles(asList("lib/annotations.bin", "lib/redist/annotations.bin")));
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "lib/annotations.bin", CHECKSUMS.ANNOTATIONS_CHANGED_JAR_BIN),
      new DeleteAction(patch, "lib64/annotations.bin", CHECKSUMS.ANNOTATIONS_JAR_BIN),
      new CreateAction(patch, "lib/redist/"),
      new CreateAction(patch, "lib64/redist/"),
      new UpdateAction(patch, "lib/redist/annotations.bin", "lib64/annotations.bin", CHECKSUMS.ANNOTATIONS_JAR_BIN, true),
      new UpdateAction(patch, "lib64/redist/annotations.bin", "lib64/annotations.bin", CHECKSUMS.ANNOTATIONS_JAR_BIN, true));
  }

  @Test
  public void testSaveLoad() throws Exception {
    Patch original = createPatch();
    File f = getTempFile("file");
    try (FileOutputStream out = new FileOutputStream(f)) {
      original.write(out);
    }
    Patch recreated;
    try (FileInputStream in = new FileInputStream(f)) {
      recreated = new Patch(in);
    }
    assertThat(recreated.getActions()).isEqualTo(original.getActions());
  }

  @Test
  public void testNoSymlinkNoise() throws IOException {
    assumeNioSymLinkCreationIsSupported();

    Files.write(new File(myOlderDir, "bin/_target").toPath(), "test".getBytes(StandardCharsets.UTF_8));
    Files.createSymbolicLink(new File(myOlderDir, "bin/_link").toPath(), Paths.get("_target"));
    resetNewerDir();

    Patch patch = createPatch();
    assertThat(patch.getActions()).isEmpty();
  }

  @Test
  public void testSymlinkDereferenceAndMove() throws IOException {
    assumeNioSymLinkCreationIsSupported();

    long checksum = randomFile(myOlderDir.toPath().resolve("bin/mac_lib.jnilib"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("bin/mac_lib.dylib"), Paths.get("mac_lib.jnilib"));
    resetNewerDir();
    Utils.delete(new File(myNewerDir, "bin/mac_lib.dylib"));
    Files.createDirectories(new File(myNewerDir, "plugins/whatever/bin").toPath());
    Files.move(new File(myNewerDir, "bin/mac_lib.jnilib").toPath(), new File(myNewerDir, "plugins/whatever/bin/mac_lib.dylib").toPath());

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "bin/mac_lib.dylib", linkHash("mac_lib.jnilib")),
      new DeleteAction(patch, "bin/mac_lib.jnilib", checksum),
      new CreateAction(patch, "plugins/"),
      new CreateAction(patch, "plugins/whatever/"),
      new CreateAction(patch, "plugins/whatever/bin/"),
      new CreateAction(patch, "plugins/whatever/bin/mac_lib.dylib"));
  }

  @Test
  public void testValidatingSymlinkToDirectory() throws Exception {
    assumeSymLinkCreationIsSupported();

    resetNewerDir();
    Files.createDirectories(myOlderDir.toPath().resolve("other_dir"));
    IoTestUtil.createSymbolicLink(myOlderDir.toPath().resolve("dir"), Paths.get("other_dir"));
    Files.createDirectories(myNewerDir.toPath().resolve("dir"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "dir", linkHash("other_dir")),
      new DeleteAction(patch, "other_dir/", Digester.DIRECTORY),
      new CreateAction(patch, "dir/"));

    assertThat(patch.validate(myOlderDir, TEST_UI)).isEmpty();
  }

  @Test
  public void testValidatingMultipleSymlinkConversion() throws Exception {
    assumeSymLinkCreationIsSupported();

    resetNewerDir();

    randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Libraries/lib.dylib"));
    randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Resources/r/res.bin"));
    IoTestUtil.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Versions/Current"), Paths.get("A"));
    IoTestUtil.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Libraries"), Paths.get("Versions/Current/Libraries"));
    IoTestUtil.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Resources"), Paths.get("Versions/Current/Resources"));

    randomFile(myNewerDir.toPath().resolve("A.framework/Libraries/lib.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Resources/r/res.bin"));

    Patch patch = createPatch();
    assertThat(patch.validate(myOlderDir, TEST_UI)).isEmpty();
  }

  private Patch createCaseOnlyRenamePatch() throws IOException {
    Patch patch = createPatch();
    assertThat(patch.getActions().get(0))
      .isInstanceOf(DeleteAction.class)
      .hasFieldOrPropertyWithValue("path", "bin/idea.bat");
    patch.getActions().add(1, new CreateAction(patch, "bin/IDEA.bat")); // simulates rename "idea.bat" -> "IDEA.bat"
    return patch;
  }

  private static long linkHash(String target) throws IOException {
    return Digester.digestStream(new ByteArrayInputStream(target.getBytes(StandardCharsets.UTF_8))) | Digester.SYM_LINK;
  }
}
