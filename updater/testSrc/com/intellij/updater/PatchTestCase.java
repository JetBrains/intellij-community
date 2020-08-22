// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.zip.CRC32;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class PatchTestCase extends UpdaterTestCase {
  protected File myNewerDir;
  protected File myOlderDir;

  @Override
  public void before() throws Exception {
    super.before();

    myOlderDir = getTempFile("oldDir");
    myNewerDir = getTempFile("newDir");
    FileUtil.copyDir(dataDir, myOlderDir);
    FileUtil.copyDir(dataDir, myNewerDir);

    FileUtil.delete(new File(myNewerDir, "bin/idea.bat"));
    FileUtil.writeToFile(new File(myNewerDir, "Readme.txt"), "hello");
    FileUtil.writeToFile(new File(myNewerDir, "newDir/newFile.txt"), "hello");

    FileUtil.delete(new File(myOlderDir, "lib/annotations_changed.jar"));
    FileUtil.delete(new File(myNewerDir, "lib/annotations.jar"));
    FileUtil.rename(new File(myNewerDir, "lib/annotations_changed.jar"),
                    new File(myNewerDir, "lib/annotations.jar"));

    FileUtil.delete(new File(myOlderDir, "lib/bootstrap_deleted.jar"));
    FileUtil.delete(new File(myNewerDir, "lib/bootstrap.jar"));
    FileUtil.rename(new File(myNewerDir, "lib/bootstrap_deleted.jar"),
                    new File(myNewerDir, "lib/bootstrap.jar"));

    FileUtil.delete(new File(myOlderDir, "lib/boot2_changed_with_unchanged_content.jar"));
    FileUtil.delete(new File(myNewerDir, "lib/boot2.jar"));
    FileUtil.rename(new File(myNewerDir, "lib/boot2_changed_with_unchanged_content.jar"),
                    new File(myNewerDir, "lib/boot2.jar"));
  }

  protected Patch createPatch() throws IOException {
    return createPatch(Function.identity());
  }

  protected Patch createPatch(Function<PatchSpec, PatchSpec> tuner) throws IOException {
    PatchSpec spec = tuner.apply(new PatchSpec()
      .setOldFolder(myOlderDir.getAbsolutePath())
      .setNewFolder(myNewerDir.getAbsolutePath()));
    return new Patch(spec, TEST_UI);
  }

  protected void resetNewerDir() throws IOException {
    Utils.delete(myNewerDir);
    Utils.copyDirectory(myOlderDir.toPath(), myNewerDir.toPath());
  }

  protected static Map<String, Long> digest(Patch patch, File dir) throws IOException {
    return new TreeMap<>(patch.digestFiles(dir, Collections.emptySet(), false));
  }

  protected static List<PatchAction> sortActions(List<PatchAction> actions) {
    return sort(actions, a -> a.getClass().getSimpleName().charAt(0), Comparator.comparing(PatchAction::getPath));
  }

  protected static List<ValidationResult> sortResults(List<ValidationResult> results) {
    return sort(results, r -> r.action, Comparator.comparing(r -> r.path));
  }

  private static <T> List<T> sort(List<T> list, Function<T, ?> classifier, Comparator<T> sorter) {
    // splits the list into groups
    Collection<List<T>> groups = list.stream().collect(groupingBy(classifier, LinkedHashMap::new, toList())).values();
    // verifies the list is monotonic
    assertThat(list).isEqualTo(groups.stream().flatMap(Collection::stream).collect(toList()));
    // sorts group elements and concatenates groups into a list
    return groups.stream()
      .flatMap(elements -> elements.stream().sorted(sorter))
      .collect(toList());
  }

  protected static long randomFile(Path file) throws IOException {
    Random rnd = new Random();
    int size = (1 + rnd.nextInt(1023)) * 1024;
    byte[] data = new byte[size];
    rnd.nextBytes(data);

    Files.createDirectories(file.getParent());
    Files.write(file, data);

    CRC32 crc32 = new CRC32();
    crc32.update(data);
    return crc32.getValue();
  }
}