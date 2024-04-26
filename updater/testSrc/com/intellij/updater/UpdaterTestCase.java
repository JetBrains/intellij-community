// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(UpdaterTestCase.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface UpdaterTest { }

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@interface UpdaterTestData { }

final class UpdaterTestCase implements BeforeAllCallback, AfterEachCallback {
  static final long README_TXT = 7256327L;
  static final long IDEA_BAT = 1681106766L;
  static final long ANNOTATIONS_JAR = 2525796836L;
  static final long ANNOTATIONS_CHANGED_JAR = 2587736223L;
  static final long BOOT_JAR = 2697993201L;
  static final long BOOT_CHANGED_JAR = 2957038758L;
  static final long BOOTSTRAP_JAR = 2745721972L;
  static final long BOOTSTRAP_DELETED_JAR = 811764767L;
  static final long LINK_TO_README_TXT = 2305843011042707672L;
  static final long LINK_TO_DOT_README_TXT_DOS = 2305843011210142148L;
  static final long LINK_TO_DOT_README_TXT_UNIX = 2305843009503057206L;

  private static Path ourDataDir;

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    System.setProperty("idea.required.space", Long.toString(20_000_000));

    if (ourDataDir == null) {
      var dir = Path.of("community/updater/testData");
      if (!Files.exists(dir)) dir = Path.of("updater/testData");
      if (!Files.exists(dir)) dir = Path.of("testData");
      if (!Files.exists(dir)) throw new IllegalStateException("Cannot find test data directory under " + Path.of(".").toAbsolutePath());
      ourDataDir = dir.toAbsolutePath();
    }

    Runner.checkCaseSensitivity(ourDataDir.toString());

    @SuppressWarnings("OptionalGetWithoutIsPresent") var testInstance = context.getTestInstance().get();
    var testClass = testInstance.getClass();
    while (!testClass.isAnnotationPresent(UpdaterTest.class)) testClass = testClass.getSuperclass();
    for (var field : testClass.getDeclaredFields()) {
      if (field.isAnnotationPresent(UpdaterTestData.class)) {
        field.set(testInstance, ourDataDir);
      }
    }
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    Utils.cleanup();
  }

  static void setReadOnly(Path file) throws IOException {
    if (Utils.IS_WINDOWS) {
      Files.getFileAttributeView(file, DosFileAttributeView.class).setReadOnly(true);
    }
    else {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--------"));
    }
  }

  final static class Directories {
    final Path oldDir, newDir;

    Directories(Path oldDir, Path newDir) {
      this.oldDir = oldDir;
      this.newDir = newDir;
    }
  }

  static Directories prepareDirectories(Path tempDir, Path dataDir, boolean mangle) throws IOException {
    var oldDir = Files.createDirectory(tempDir.resolve("oldDir"));
    Utils.copyDirectory(dataDir, oldDir);
    Files.writeString(oldDir.resolve("Readme.txt"), Files.readString(dataDir.resolve("Readme.txt")).replace("\r\n", "\n"));
    Files.writeString(oldDir.resolve("bin/idea.bat"), Files.readString(dataDir.resolve("bin/idea.bat")).replace("\r\n", "\n"));
    Files.delete(oldDir.resolve("lib/annotations_changed.jar"));
    Files.delete(oldDir.resolve("lib/bootstrap_deleted.jar"));

    var newDir = Files.createDirectory(tempDir.resolve("newDir"));
    if (mangle) {
      Utils.copyDirectory(dataDir, newDir);
      Files.writeString(newDir.resolve("Readme.txt"), "hello");
      Files.delete(newDir.resolve("bin/idea.bat"));
      Utils.writeString(newDir.resolve("newDir/newFile.txt"), "hello");
      Files.delete(newDir.resolve("lib/annotations.jar"));
      Files.move(newDir.resolve("lib/annotations_changed.jar"), newDir.resolve("lib/annotations.jar"));
      Files.delete(newDir.resolve("lib/bootstrap.jar"));
      Files.move(newDir.resolve("lib/bootstrap_deleted.jar"), newDir.resolve("lib/bootstrap.jar"));
    }
    else {
      Utils.copyDirectory(oldDir, newDir);
    }

    return new Directories(oldDir, newDir);
  }

  static PatchSpec createPatchSpec(Path oldDir, Path newDir) {
    return new PatchSpec()
      .setOldFolder(oldDir.toString()).setOldVersionDescription("<old>")
      .setNewFolder(newDir.toString()).setNewVersionDescription("<new>");
  }

  static List<PatchAction> sortActions(List<PatchAction> actions) {
    return sort(actions, a -> a.getClass().getSimpleName().charAt(0), Comparator.comparing(PatchAction::getPath));
  }

  static List<ValidationResult> sortResults(List<ValidationResult> results) {
    return sort(results, r -> r.action, Comparator.comparing(r -> r.path));
  }

  private static <T> List<T> sort(List<T> list, Function<T, ?> classifier, Comparator<T> sorter) {
    // splits the list into groups
    var groups = list.stream().collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.toList())).values();
    // verifies the list is monotonic
    assertThat(list).isEqualTo(groups.stream().flatMap(Collection::stream).collect(Collectors.toList()));
    // sorts group elements and concatenates groups into a list
    return groups.stream()
      .flatMap(elements -> elements.stream().sorted(sorter))
      .collect(Collectors.toList());
  }

  static long randomFile(Path file) throws IOException {
    var rnd = new Random();
    var size = (1 + rnd.nextInt(1023)) * 1024;
    var data = new byte[size];
    rnd.nextBytes(data);

    Files.createDirectories(file.getParent());
    Files.write(file, data);

    var crc32 = new CRC32();
    crc32.update(data);
    return crc32.getValue();
  }

  static long linkHash(String target) throws IOException {
    return Digester.digestStream(new ByteArrayInputStream(target.getBytes(StandardCharsets.UTF_8))) | Digester.SYM_LINK;
  }
}
