// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import com.intellij.openapi.util.io.FileUtil;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeFalse;

public class SymlinkPatchTest extends PatchTestCase {
  @Override
  public void setUp() throws Exception {
    assumeFalse(Utils.IS_WINDOWS);

    super.setUp();

    FileUtil.writeToFile(new File(myOlderDir, "Readme.txt"), "hello");
    resetNewerDir();
  }

  @Test
  public void same() throws Exception {
    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));
    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.link"));
    assertThat(createPatch().getActions()).containsExactly();
  }

  @Test
  public void create() throws Exception {
    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.link"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void delete() throws Exception {
    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.link", CHECKSUMS.LINK_TO_README_TXT));
  }

  @Test
  public void rename() throws Exception {
    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.lnk"));
    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.link"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.lnk", CHECKSUMS.LINK_TO_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void retarget() throws Exception {
    Utils.createLink("Readme.txt", new File(myOlderDir, "Readme.link"));
    Utils.createLink("./Readme.txt", new File(myNewerDir, "Readme.link"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.link", CHECKSUMS.LINK_TO_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void renameAndRetarget() throws Exception {
    Utils.createLink("./Readme.txt", new File(myOlderDir, "Readme.lnk"));
    Utils.createLink("Readme.txt", new File(myNewerDir, "Readme.link"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.lnk", CHECKSUMS.LINK_TO_DOT_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void multipleDirectorySymlinks() throws Exception {
    long l1 = randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Libraries/lib1.dylib"));
    long l2 = randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Libraries/lib2.dylib"));
    long r1 = randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Resources/r1.bin"));
    long r2 = randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Resources/r2.bin"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Versions/Current"), Paths.get("A"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Libraries"), Paths.get("Versions/Current/Libraries"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Resources"), Paths.get("Versions/Current/Resources"));

    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Libraries/lib1.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Libraries/lib2.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Resources/r1.bin"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Resources/r2.bin"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Versions/Current"), Paths.get("A"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Libraries"), Paths.get("Versions/Current/Libraries"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Resources"), Paths.get("Versions/Current/Resources"));

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new UpdateAction(patch, "A.framework/Versions/A/Libraries/lib1.dylib", l1),
      new UpdateAction(patch, "A.framework/Versions/A/Libraries/lib2.dylib", l2),
      new UpdateAction(patch, "A.framework/Versions/A/Resources/r1.bin", r1),
      new UpdateAction(patch, "A.framework/Versions/A/Resources/r2.bin", r2));
  }

  private static long randomFile(Path file) throws IOException {
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