// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import com.intellij.openapi.util.io.IoTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
    @NotNull Path link1 = new File(myOlderDir, "Readme.link").toPath();
    @NotNull Path target1 = Paths.get("Readme.txt");
    Files.createSymbolicLink(link1, target1);
    @NotNull Path link = new File(myNewerDir, "Readme.link").toPath();
    @NotNull Path target = Paths.get("Readme.txt");
    Files.createSymbolicLink(link, target);
    assertThat(createPatch().getActions()).containsExactly();
  }

  @Test
  public void create() throws Exception {
    @NotNull Path link = new File(myNewerDir, "Readme.link").toPath();
    @NotNull Path target = Paths.get("Readme.txt");
    Files.createSymbolicLink(link, target);

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void delete() throws Exception {
    @NotNull Path link = new File(myOlderDir, "Readme.link").toPath();
    @NotNull Path target = Paths.get("Readme.txt");
    Files.createSymbolicLink(link, target);

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.link", CHECKSUMS.LINK_TO_README_TXT));
  }

  @Test
  public void rename() throws Exception {
    @NotNull Path link1 = new File(myOlderDir, "Readme.lnk").toPath();
    @NotNull Path target1 = Paths.get("Readme.txt");
    Files.createSymbolicLink(link1, target1);
    @NotNull Path link = new File(myNewerDir, "Readme.link").toPath();
    @NotNull Path target = Paths.get("Readme.txt");
    Files.createSymbolicLink(link, target);

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.lnk", CHECKSUMS.LINK_TO_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void retarget() throws Exception {
    @NotNull Path link1 = new File(myOlderDir, "Readme.link").toPath();
    @NotNull Path target1 = Paths.get("Readme.txt");
    Files.createSymbolicLink(link1, target1);
    @NotNull Path link = new File(myNewerDir, "Readme.link").toPath();
    @NotNull Path target = Paths.get("./Readme.txt");
    Files.createSymbolicLink(link, target);

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.link", CHECKSUMS.LINK_TO_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void renameAndRetarget() throws Exception {
    @NotNull Path link1 = new File(myOlderDir, "Readme.lnk").toPath();
    @NotNull Path target1 = Paths.get("./Readme.txt");
    Files.createSymbolicLink(link1, target1);
    @NotNull Path link = new File(myNewerDir, "Readme.link").toPath();
    @NotNull Path target = Paths.get("Readme.txt");
    Files.createSymbolicLink(link, target);

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.lnk", CHECKSUMS.LINK_TO_DOT_README_TXT),
      new CreateAction(patch, "Readme.link"));
  }

  @Test
  public void fileToLink() throws Exception {
    Files.move(new File(myNewerDir, "Readme.txt").toPath(), new File(myNewerDir, "Readme.md").toPath());
    @NotNull Path link = new File(myNewerDir, "Readme.txt").toPath();
    @NotNull Path target = Paths.get("Readme.md");
    Files.createSymbolicLink(link, target);

    Patch patch = createPatch();
    assertThat(sortActions(patch.getActions())).containsExactly(
      new DeleteAction(patch, "Readme.txt", CHECKSUMS.README_TXT),
      new CreateAction(patch, "Readme.md"),
      new CreateAction(patch, "Readme.txt"));
  }

  @Test
  public void multipleDirectorySymlinks() throws Exception {
    long l1 = randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Libraries/lib1.dylib"));
    long l2 = randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Libraries/lib2.dylib"));
    long r1 = randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Resources/r1.bin"));
    long r2 = randomFile(myOlderDir.toPath().resolve("A.framework/Versions/A/Resources/r2.bin"));
    @NotNull Path target6 = Paths.get("A");
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Versions/Current"), target6);
    @NotNull Path target5 = Paths.get("Versions/Current/Libraries");
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Libraries"), target5);
    @NotNull Path target4 = Paths.get("Versions/Current/Resources");
    Files.createSymbolicLink(myOlderDir.toPath().resolve("A.framework/Resources"), target4);

    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Libraries/lib1.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Libraries/lib2.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Resources/r1.bin"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/A/Resources/r2.bin"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/B/Libraries/lib1.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/B/Libraries/lib2.dylib"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/B/Resources/r1.bin"));
    randomFile(myNewerDir.toPath().resolve("A.framework/Versions/B/Resources/r2.bin"));
    @NotNull Path target3 = Paths.get("A");
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Versions/Previous"), target3);
    @NotNull Path target2 = Paths.get("B");
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Versions/Current"), target2);
    @NotNull Path target1 = Paths.get("Versions/Current/Libraries");
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Libraries"), target1);
    @NotNull Path target = Paths.get("Versions/Current/Resources");
    Files.createSymbolicLink(myNewerDir.toPath().resolve("A.framework/Resources"), target);

    Patch patch = createPatch();
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
