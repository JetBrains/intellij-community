// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import com.intellij.openapi.util.io.IoTestUtil;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class SymlinkPatchTest extends PatchTestCase {
  @Override
  public void before() throws Exception {
    IoTestUtil.assumeSymLinkCreationIsSupported();

    super.before();

    resetNewerDir();
  }

  @Test
  public void same() throws Exception {
    var target = Path.of("Readme.txt");
    Files.createSymbolicLink(myOlderDir.toPath().resolve("Readme.link"), target);
    Files.createSymbolicLink(myNewerDir.toPath().resolve("Readme.link"), target);
    assertThat(createPatch().getActions()).containsExactly();
  }

  @Test
  public void create() throws Exception {
    Files.createSymbolicLink(myNewerDir.toPath().resolve("Readme.link"), Path.of("Readme.txt"));

    var patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void delete() throws Exception {
    Files.createSymbolicLink(myOlderDir.toPath().resolve("Readme.link"), Path.of("Readme.txt"));

    var patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.link", CHECKSUMS.LINK_TO_README_TXT));
  }

  @Test
  public void rename() throws Exception {
    var target = Path.of("Readme.txt");
    Files.createSymbolicLink(myOlderDir.toPath().resolve("Readme.lnk"), target);
    Files.createSymbolicLink(myNewerDir.toPath().resolve("Readme.link"), target);

    var patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.lnk", CHECKSUMS.LINK_TO_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void retarget() throws Exception {
    Files.createSymbolicLink(myOlderDir.toPath().resolve("Readme.link"), Path.of("Readme.txt"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("Readme.link"), Path.of("./Readme.txt"));

    var patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.link", CHECKSUMS.LINK_TO_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void renameAndRetarget() throws Exception {
    Files.createSymbolicLink(myOlderDir.toPath().resolve("Readme.lnk"), Path.of("./Readme.txt"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("Readme.link"), Path.of("Readme.txt"));

    var patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.lnk", CHECKSUMS.LINK_TO_DOT_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void fileToLink() throws Exception {
    Files.move(myNewerDir.toPath().resolve("Readme.txt"), myNewerDir.toPath().resolve("Readme.md"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("Readme.txt"), Path.of("Readme.md"));

    var patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.txt", CHECKSUMS.README_TXT),
      new CreateAction(patch, "Readme.md"),
      new CreateAction(patch, "Readme.txt"));
  }

  @Test
  @SuppressWarnings("DuplicateExpressions")
    public void multipleDirectorySymlinks() throws Exception {
    var l1 = randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Libraries/lib1.dylib"));
    var l2 = randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Libraries/lib2.dylib"));
    var r1 = randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Resources/r1.bin"));
    var r2 = randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Resources/r2.bin"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Versions/Current"), Path.of("A"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Libraries"), Path.of("Versions/Current/Libraries"));
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Resources"), Path.of("Versions/Current/Resources"));

    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Libraries/lib1.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Libraries/lib2.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Resources/r1.bin"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Resources/r2.bin"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/B/Libraries/lib1.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/B/Libraries/lib2.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/B/Resources/r1.bin"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/B/Resources/r2.bin"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Versions/Previous"), Path.of("A"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Versions/Current"), Path.of("B"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Libraries"), Path.of("Versions/Current/Libraries"));
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Resources"), Path.of("Versions/Current/Resources"));

    var patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "A.framework/Versions/Current", 2305843012767948427L),  // = crc32("A") | SYM_LINK
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
