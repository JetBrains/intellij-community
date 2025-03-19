// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsCheckoutProcessor;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectSetProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.RunInEdt;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.rules.ProjectModelExtension;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
@RunInEdt(writeIntent = true)
public class ProjectSetTest  {

  @RegisterExtension
  private final ProjectModelExtension projectModel = new ProjectModelExtension();

  private VirtualFile sourceRootDir;
  @BeforeEach
  void setUp() {
    sourceRootDir = projectModel.getBaseProjectDir().newVirtualDirectory("src");
  }

  @Test
  public void testProjectSetReader(@TestDisposable Disposable testRootDisposable) throws IOException {
    final Ref<List<? extends Pair<String, String>>> ref = Ref.create();
    ProjectSetProcessor.EXTENSION_POINT_NAME.getPoint().registerExtension(new ProjectSetProcessor() {
      @Override
      public String getId() {
        return "test";
      }

      @Override
      public void processEntries(@NotNull List<? extends Pair<String, String>> entries1, @NotNull Context context1, @NotNull Runnable runNext) {
        ref.set(entries1);
      }
    }, testRootDisposable);

    ProjectSetProcessor.Context context = new ProjectSetProcessor.Context();
    context.directory = sourceRootDir;
    readDescriptor(new File(testDataPath() + "descriptor.json"), context);

    List<? extends Pair<String, String>> entries = ref.get();
    assertEquals(2, entries.size());
    assertEquals("git://foo.bar", entries.get(0).getSecond());
    assertEquals("{\"foo\":\"bar\"}", entries.get(1).getSecond());
  }

  @Test
  public void testVcsCheckoutProcessor(@TestDisposable Disposable testRootDisposable) throws IOException {
    final List<Pair<String, String>> pairs = new ArrayList<>();
    VcsCheckoutProcessor.EXTENSION_POINT_NAME.getPoint().registerExtension(new VcsCheckoutProcessor() {
      @NotNull
      @Override
      public String getId() {
        return "schema";
      }

      @Override
      public boolean checkout(@NotNull Map<String, String> parameters,
                              @NotNull VirtualFile parentDirectory, @NotNull String directoryName) {
        pairs.add(Pair.create(parameters.get("url"), directoryName));
        return true;
      }
    }, testRootDisposable);

    ProjectSetProcessor.Context context = new ProjectSetProcessor.Context();
    context.directoryName = "newDir";
    context.directory = sourceRootDir;
    readDescriptor(new File(testDataPath() + "vcs.json"), context);
    Collections.sort(pairs, (o1, o2) -> o2.first.compareTo(o1.first));
    assertEquals(Pair.create("schema://foo.bar/path", "test"), pairs.get(1));
    assertEquals(Pair.create("schema://foo.bar1/path1", "test/custom"), pairs.get(0));
  }

  @Test
  public void testOpenProject() throws IOException {
    doOpenProject("project.json", "untitled");
  }

  @Test
  public void testDefault() throws IOException {
    doOpenProject("default.json", "projectSet");
  }


  // ============================== infrastructure: ============================================================

  private static String testDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath() + "projectSet/";
  }

  private static void doOpenProject(@NotNull String file,
                                    @NotNull String projectName) throws IOException {
    ProjectSetProcessor.Context context = new ProjectSetProcessor.Context();
    context.directory = VfsUtil.findFileByIoFile(new File(testDataPath()), true);
    readDescriptor(new File(testDataPath() + file), context);
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    Project project = ContainerUtil.find(projects, project1 -> projectName.equals(project1.getName()));
    assertNotNull(project);
    ProjectManagerEx.getInstanceEx().forceCloseProject(project);
  }


  private static void readDescriptor(@NotNull File descriptor,
                                     @Nullable ProjectSetProcessor.Context context) throws IOException {
    try (InputStreamReader input = new InputStreamReader(new FileInputStream(descriptor), StandardCharsets.UTF_8)) {
      JsonElement parse = new JsonParser().parse(input);
      new ProjectSetReader().readDescriptor(parse.getAsJsonObject(), context);
    }
  }
}
