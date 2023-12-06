// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;

import static com.intellij.updater.UpdaterTestCase.*;
import static org.assertj.core.api.Assertions.assertThat;

@UpdaterTest
class PatchCreationTest {
  @TempDir Path tempDir;
  @UpdaterTestData Path dataDir;

  private final ConsoleUpdaterUI testUI = new ConsoleUpdaterUI(false);

  @Test void digestFiles() throws Exception {
    var patch = new Patch(createPatchSpec(dataDir, dataDir));
    assertThat(patch.digestFiles(dataDir, Set.of())).hasSize(11);
  }

  @Test void basics() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "bin/idea.bat", UpdaterTestCase.IDEA_BAT),
      new CreateAction(patch, "newDir/"),
      new CreateAction(patch, "newDir/newFile.txt"),
      new UpdateAction(patch, "Readme.txt", UpdaterTestCase.README_TXT),
      new UpdateAction(patch, "lib/annotations.jar", UpdaterTestCase.ANNOTATIONS_JAR),
      new UpdateAction(patch, "lib/bootstrap.jar", UpdaterTestCase.BOOTSTRAP_JAR));
  }

  @Test void creatingWithIgnoredFiles() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir).setIgnoredFiles(List.of("Readme.txt", "bin/idea.bat")));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new CreateAction(patch, "newDir/"),
      new CreateAction(patch, "newDir/newFile.txt"),
      new UpdateAction(patch, "lib/annotations.jar", UpdaterTestCase.ANNOTATIONS_JAR),
      new UpdateAction(patch, "lib/bootstrap.jar", UpdaterTestCase.BOOTSTRAP_JAR));
  }

  @Test void validation() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));
    Files.writeString(dirs.oldDir.resolve("bin/idea.bat"), "changed");
    Files.createDirectory(dirs.oldDir.resolve("extraDir"));
    Files.writeString(dirs.oldDir.resolve("extraDir/extraFile.txt"), "");
    Files.createDirectory(dirs.oldDir.resolve("newDir"));
    Files.writeString(dirs.oldDir.resolve("newDir/newFile.txt"), "");
    Files.writeString(dirs.oldDir.resolve("Readme.txt"), "changed");
    Files.writeString(dirs.oldDir.resolve("lib/annotations.jar"), "changed");
    Files.delete(dirs.oldDir.resolve("lib/bootstrap.jar"));

    assertThat(sortResults(patch.validate(dirs.oldDir.toFile(), testUI))).containsExactly(
      new ValidationResult(ValidationResult.Kind.CONFLICT,
                           "bin/idea.bat",
                           ValidationResult.Action.DELETE,
                           "Modified",
                           ValidationResult.Option.DELETE, ValidationResult.Option.KEEP),
      new ValidationResult(ValidationResult.Kind.CONFLICT,
                           "newDir/",
                           ValidationResult.Action.CREATE,
                           "Already exists",
                           ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP),
      new ValidationResult(ValidationResult.Kind.CONFLICT,
                           "newDir/newFile.txt",
                           ValidationResult.Action.CREATE,
                           "Already exists",
                           ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP),
      new ValidationResult(ValidationResult.Kind.ERROR,
                           "Readme.txt",
                           ValidationResult.Action.UPDATE,
                           "Modified",
                           ValidationResult.Option.IGNORE),
      new ValidationResult(ValidationResult.Kind.ERROR,
                           "lib/annotations.jar",
                           ValidationResult.Action.UPDATE,
                           "Modified",
                           ValidationResult.Option.IGNORE),
      new ValidationResult(ValidationResult.Kind.ERROR,
                           "lib/bootstrap.jar",
                           ValidationResult.Action.UPDATE,
                           "Absent",
                           ValidationResult.Option.IGNORE));
  }

  @Test void validatingCaseOnlyRename() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));
    simulateCaseOnlyRename(patch);

    assertThat(patch.validate(dirs.oldDir.toFile(), testUI)).isEmpty();
  }

  @Test void validatingCaseOnlyRenameWithConflict() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));
    simulateCaseOnlyRename(patch);
    Files.writeString(dirs.oldDir.resolve("bin/IDEA.bat"), Files.readString(dirs.oldDir.resolve("bin/idea.bat")));

    var results = patch.validate(dirs.oldDir.toFile(), testUI);
    if (Runner.isCaseSensitiveFs()) {
      assertThat(results).containsExactly(
        new ValidationResult(ValidationResult.Kind.CONFLICT,
          "bin/IDEA.bat",
          ValidationResult.Action.CREATE,
          "Already exists",
          ValidationResult.Option.REPLACE, ValidationResult.Option.KEEP));
    }
    else {
      assertThat(results).isEmpty();
    }
  }

  private static void simulateCaseOnlyRename(Patch patch) {
    assertThat(patch.getActions().get(0))
      .isInstanceOf(DeleteAction.class)
      .hasFieldOrPropertyWithValue("path", "bin/idea.bat");
    patch.getActions().add(1, new CreateAction(patch, "bin/IDEA.bat")); // simulates rename "idea.bat" -> "IDEA.bat"
  }

  @Test void validationWithOptionalFiles() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);

    var patch1 = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));
    Files.copy(dirs.oldDir.resolve("lib/boot.jar"), dirs.oldDir.resolve("lib/annotations.jar"), StandardCopyOption.REPLACE_EXISTING);
    assertThat(patch1.validate(dirs.oldDir.toFile(), testUI)).containsExactly(
      new ValidationResult(ValidationResult.Kind.ERROR,
                           "lib/annotations.jar",
                           ValidationResult.Action.UPDATE,
                           "Modified",
                           ValidationResult.Option.IGNORE));

    var patch2 = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir).setOptionalFiles(List.of("lib/annotations.jar")));
    Files.delete(dirs.oldDir.resolve("lib/annotations.jar"));
    assertThat(patch2.validate(dirs.oldDir.toFile(), testUI)).isEmpty();
  }

  @Test void validatingNonAccessibleFiles() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, true);
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    var file = dirs.oldDir.resolve("Readme.txt");
    try (var channel = FileChannel.open(file, StandardOpenOption.APPEND); var ignored = channel.lock()) {
      var message = Utils.IS_WINDOWS ? "Locked by: [" + ProcessHandle.current().pid() + "] OpenJDK Platform binary" : "Access denied";
      var option = Utils.IS_WINDOWS ? ValidationResult.Option.KILL_PROCESS : ValidationResult.Option.IGNORE;
      assertThat(patch.validate(dirs.oldDir.toFile(), testUI)).containsExactly(
        new ValidationResult(ValidationResult.Kind.ERROR,
                             "Readme.txt",
                             ValidationResult.Action.UPDATE,
                             message,
                             option));
    }
  }

  @Test void zipFileMove() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Utils.move(dirs.newDir.resolve("lib/annotations.jar"), dirs.newDir.resolve("lib/redist/annotations.jar"), false);
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "lib/annotations.jar", UpdaterTestCase.ANNOTATIONS_JAR),
      new CreateAction(patch, "lib/redist/"),
      new UpdateAction(patch, "lib/redist/annotations.jar", "lib/annotations.jar", UpdaterTestCase.ANNOTATIONS_JAR, true));
  }

  @Test void zipFileMoveWithUpdate() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Files.delete(dirs.newDir.resolve("lib/annotations.jar"));
    Utils.copy(dataDir.resolve("lib/annotations_changed.jar"), dirs.newDir.resolve("lib/redist/annotations.jar"), false);
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "lib/annotations.jar", UpdaterTestCase.ANNOTATIONS_JAR),
      new CreateAction(patch, "lib/redist/"),
      new UpdateAction(patch, "lib/redist/annotations.jar", "lib/annotations.jar", UpdaterTestCase.ANNOTATIONS_JAR, false));
  }

  @Test void zipFileMoveWithAlternatives() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Utils.copy(dirs.oldDir.resolve("lib/annotations.jar"), dirs.oldDir.resolve("lib64/annotations.jar"), false);
    Utils.copy(dirs.newDir.resolve("lib/annotations.jar"), dirs.newDir.resolve("lib64/redist/annotations.jar"), false);
    Utils.move(dirs.newDir.resolve("lib/annotations.jar"), dirs.newDir.resolve("lib/redist/annotations.jar"), false);
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "lib/annotations.jar", UpdaterTestCase.ANNOTATIONS_JAR),
      new DeleteAction(patch, "lib64/annotations.jar", UpdaterTestCase.ANNOTATIONS_JAR),
      new CreateAction(patch, "lib/redist/"),
      new CreateAction(patch, "lib64/redist/"),
      new UpdateAction(patch, "lib/redist/annotations.jar", "lib/annotations.jar", UpdaterTestCase.ANNOTATIONS_JAR, true),
      new UpdateAction(patch, "lib64/redist/annotations.jar", "lib64/annotations.jar", UpdaterTestCase.ANNOTATIONS_JAR, true));
  }

  @Test void noOptionalFileMove1() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Utils.copy(dataDir.resolve("lib/annotations.jar"), dirs.oldDir.resolve("lib/annotations.bin"), false);
    Utils.copy(dataDir.resolve("lib/annotations_changed.jar"), dirs.oldDir.resolve("lib64/annotations.bin"), false);
    Utils.copy(dataDir.resolve("lib/annotations.jar"), dirs.newDir.resolve("lib/redist/annotations.bin"), false);
    Utils.copy(dataDir.resolve("lib/annotations.jar"), dirs.newDir.resolve("lib64/redist/annotations.bin"), false);

    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir).setOptionalFiles(List.of("lib/annotations.bin", "lib/redist/annotations.bin")));
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "lib/annotations.bin", UpdaterTestCase.ANNOTATIONS_JAR),
      new DeleteAction(patch, "lib64/annotations.bin", UpdaterTestCase.ANNOTATIONS_CHANGED_JAR),
      new CreateAction(patch, "lib/redist/"),
      new CreateAction(patch, "lib64/redist/"),
      new UpdateAction(patch, "lib/redist/annotations.bin", "lib/annotations.bin", UpdaterTestCase.ANNOTATIONS_JAR, true),
      new UpdateAction(patch, "lib64/redist/annotations.bin", "lib64/annotations.bin", UpdaterTestCase.ANNOTATIONS_CHANGED_JAR, false));
  }

  @Test void noOptionalFileMove2() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Utils.copy(dataDir.resolve("lib/annotations_changed.jar"), dirs.oldDir.resolve("lib/annotations.bin"), false);
    Utils.copy(dataDir.resolve("lib/annotations.jar"), dirs.oldDir.resolve("lib64/annotations.bin"), false);
    Utils.copy(dataDir.resolve("lib/annotations.jar"), dirs.newDir.resolve("lib/redist/annotations.bin"), false);
    Utils.copy(dataDir.resolve("lib/annotations.jar"), dirs.newDir.resolve("lib64/redist/annotations.bin"), false);

    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir).setOptionalFiles(List.of("lib/annotations.bin", "lib/redist/annotations.bin")));
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "lib/annotations.bin", UpdaterTestCase.ANNOTATIONS_CHANGED_JAR),
      new DeleteAction(patch, "lib64/annotations.bin", UpdaterTestCase.ANNOTATIONS_JAR),
      new CreateAction(patch, "lib/redist/"),
      new CreateAction(patch, "lib64/redist/"),
      new UpdateAction(patch, "lib/redist/annotations.bin", "lib64/annotations.bin", UpdaterTestCase.ANNOTATIONS_JAR, true),
      new UpdateAction(patch, "lib64/redist/annotations.bin", "lib64/annotations.bin", UpdaterTestCase.ANNOTATIONS_JAR, true));
  }

  @Test void testSaveLoad() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    var original = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));
    var f = tempDir.resolve("file");
    try (var out = Files.newOutputStream(f)) {
      original.write(out);
    }
    Patch recreated;
    try (var in = Files.newInputStream(f)) {
      recreated = new Patch(in);
    }
    assertThat(recreated.getActions()).isEqualTo(original.getActions());
  }

  @SuppressWarnings("DuplicateExpressions")
  @Test void noSymlinkNoise() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Files.writeString(dirs.oldDir.resolve("bin/_target"), "test");
    Files.createSymbolicLink(dirs.oldDir.resolve("bin/_link"), Path.of("_target"));
    Files.writeString(dirs.newDir.resolve("bin/_target"), "test");
    Files.createSymbolicLink(dirs.newDir.resolve("bin/_link"), Path.of("_target"));
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(patch.getActions()).isEmpty();
  }

  @Test void testSymlinkDereferenceAndMove() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    var checksum = randomFile(dirs.oldDir.resolve("bin/mac_lib.jnilib"));
    Files.createSymbolicLink(dirs.oldDir.resolve("bin/mac_lib.dylib"), Path.of("mac_lib.jnilib"));
    Utils.copy(dirs.oldDir.resolve("bin/mac_lib.jnilib"), dirs.newDir.resolve("plugins/whatever/bin/mac_lib.dylib"), false);
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "bin/mac_lib.dylib", linkHash("mac_lib.jnilib")),
      new DeleteAction(patch, "bin/mac_lib.jnilib", checksum),
      new CreateAction(patch, "plugins/"),
      new CreateAction(patch, "plugins/whatever/"),
      new CreateAction(patch, "plugins/whatever/bin/"),
      new CreateAction(patch, "plugins/whatever/bin/mac_lib.dylib"));
  }

  @Test void validatingSymlinkToDirectory() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Files.createDirectories(dirs.oldDir.resolve("other_dir"));
    Files.createSymbolicLink(dirs.oldDir.resolve("dir"), Path.of("other_dir"));
    Files.createDirectories(dirs.newDir.resolve("dir"));
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "dir", linkHash("other_dir")),
      new DeleteAction(patch, "other_dir/", Digester.DIRECTORY),
      new CreateAction(patch, "dir/"));

    assertThat(patch.validate(dirs.oldDir.toFile(), testUI)).isEmpty();
  }

  @Test void validatingMultipleSymlinkConversion() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Libraries/lib.dylib"));
    randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Resources/r/res.bin"));
    Path target2 = Path.of("A");
    Files.createSymbolicLink(dirs.oldDir.resolve("A.framework/Versions/Current"), target2);
    Path target1 = Path.of("Versions/Current/Libraries");
    Files.createSymbolicLink(dirs.oldDir.resolve("A.framework/Libraries"), target1);
    Path target = Path.of("Versions/Current/Resources");
    Files.createSymbolicLink(dirs.oldDir.resolve("A.framework/Resources"), target);
    randomFile(dirs.newDir.resolve("A.framework/Libraries/lib.dylib"));
    randomFile(dirs.newDir.resolve("A.framework/Resources/r/res.bin"));
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(patch.validate(dirs.oldDir.toFile(), testUI)).isEmpty();
  }

  @SuppressWarnings("DuplicateExpressions")
  @Test void same() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Files.createSymbolicLink(dirs.oldDir.resolve("Readme.link"), Path.of("Readme.txt"));
    Files.createSymbolicLink(dirs.newDir.resolve("Readme.link"), Path.of("Readme.txt"));
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(patch.getActions()).containsExactly();
  }

  @Test void create() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Files.createSymbolicLink(dirs.newDir.resolve("Readme.link"), Path.of("Readme.txt"));
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new CreateAction(patch, "Readme.link"));
  }

  @Test void delete() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Files.createSymbolicLink(dirs.oldDir.resolve("Readme.link"), Path.of("Readme.txt"));
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.link", UpdaterTestCase.LINK_TO_README_TXT));
  }

  @Test void rename() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    var target = Path.of("Readme.txt");
    Files.createSymbolicLink(dirs.oldDir.resolve("Readme.lnk"), target);
    Files.createSymbolicLink(dirs.newDir.resolve("Readme.link"), target);
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.lnk", UpdaterTestCase.LINK_TO_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }

  @Test void retarget() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Files.createSymbolicLink(dirs.oldDir.resolve("Readme.link"), Path.of("Readme.txt"));
    Files.createSymbolicLink(dirs.newDir.resolve("Readme.link"), Path.of("./Readme.txt"));
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.link", UpdaterTestCase.LINK_TO_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }

  @Test void renameAndRetarget() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Files.createSymbolicLink(dirs.oldDir.resolve("Readme.lnk"), Path.of("./Readme.txt"));
    Files.createSymbolicLink(dirs.newDir.resolve("Readme.link"), Path.of("Readme.txt"));
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.lnk",
        File.separatorChar == '\\' ? UpdaterTestCase.LINK_TO_DOT_README_TXT_DOS : UpdaterTestCase.LINK_TO_DOT_README_TXT_UNIX),
      new CreateAction(patch, "Readme.link"));
  }

  @Test void fileToLink() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    Files.move(dirs.newDir.resolve("Readme.txt"), dirs.newDir.resolve("Readme.md"));
    Files.createSymbolicLink(dirs.newDir.resolve("Readme.txt"), Path.of("Readme.md"));
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.txt", UpdaterTestCase.README_TXT),
      new CreateAction(patch, "Readme.md"),
      new CreateAction(patch, "Readme.txt"));
  }

  @SuppressWarnings("DuplicateExpressions")
  @Test void multipleDirectorySymlinks() throws Exception {
    var dirs = prepareDirectories(tempDir, dataDir, false);
    var l1 = randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Libraries/lib1.dylib"));
    var l2 = randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Libraries/lib2.dylib"));
    var r1 = randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Resources/r1.bin"));
    var r2 = randomFile(dirs.oldDir.resolve("A.framework/Versions/A/Resources/r2.bin"));
    Files.createSymbolicLink(dirs.oldDir.resolve("A.framework/Versions/Current"), Path.of("A"));
    Files.createSymbolicLink(dirs.oldDir.resolve("A.framework/Libraries"), Path.of("Versions/Current/Libraries"));
    Files.createSymbolicLink(dirs.oldDir.resolve("A.framework/Resources"), Path.of("Versions/Current/Resources"));
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
    var patch = new Patch(createPatchSpec(dirs.oldDir, dirs.newDir));

    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "A.framework/Versions/Current", linkHash("A")),
      new CreateAction(patch, "A.framework/Versions/B/"),
      new CreateAction(patch, "A.framework/Versions/B/Libraries/"),
      new CreateAction(patch, "A.framework/Versions/B/Libraries/lib1.dylib"),
      new CreateAction(patch, "A.framework/Versions/B/Libraries/lib2.dylib"),
      new CreateAction(patch, "A.framework/Versions/B/Resources/"),
      new CreateAction(patch, "A.framework/Versions/B/Resources/r1.bin"),
      new CreateAction(patch, "A.framework/Versions/B/Resources/r2.bin"),
      new CreateAction(patch, "A.framework/Versions/Current"),
      new CreateAction(patch, "A.framework/Versions/Previous"),
      new UpdateAction(patch, "A.framework/Versions/A/Libraries/lib1.dylib", l1),
      new UpdateAction(patch, "A.framework/Versions/A/Libraries/lib2.dylib", l2),
      new UpdateAction(patch, "A.framework/Versions/A/Resources/r1.bin", r1),
      new UpdateAction(patch, "A.framework/Versions/A/Resources/r2.bin", r2));
  }
}
