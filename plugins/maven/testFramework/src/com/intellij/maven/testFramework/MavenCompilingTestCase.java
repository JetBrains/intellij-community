// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework;

import com.intellij.compiler.artifacts.ArtifactsTestUtil;
import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.testFramework.CompilerTester;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.io.TestFileSystemBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public abstract class MavenCompilingTestCase extends MavenMultiVersionImportingTestCase {
  protected void compileModules(final String... moduleNames) {
    compile(createModulesCompileScope(moduleNames));
  }

  protected void buildArtifacts(String... artifactNames) {
    compile(createArtifactsScope(artifactNames));
  }

  private void compile(final CompileScope scope) {
    try {
      CompilerTester tester = new CompilerTester(myProject, Arrays.asList(scope.getAffectedModules()), null);
      try {
        List<CompilerMessage> messages = tester.make(scope);
        for (CompilerMessage message : messages) {
          if (message.getCategory() == CompilerMessageCategory.ERROR) {
            fail("Compilation failed with error: " + message.getMessage());
          }
        }
      }
      finally {
        tester.tearDown();
      }
    }
    catch (Exception e) {
      ExceptionUtil.rethrow(e);
    }
  }

  private CompileScope createArtifactsScope(String[] artifactNames) {
    List<Artifact> artifacts = new ArrayList<>();
    for (String name : artifactNames) {
      artifacts.add(ArtifactsTestUtil.findArtifact(myProject, name));
    }
    return ArtifactCompileScope.createArtifactsScope(myProject, artifacts);
  }

  private CompileScope createModulesCompileScope(final String[] moduleNames) {
    final List<Module> modules = new ArrayList<>();
    for (String name : moduleNames) {
      modules.add(getModule(name));
    }
    return new ModuleCompileScope(myProject, modules.toArray(Module.EMPTY_ARRAY), false);
  }

  protected static void assertResult(VirtualFile pomFile, String relativePath, String content) throws IOException {
    assertEquals(content, loadResult(pomFile, relativePath));
  }

  protected static String loadResult(VirtualFile pomFile, String relativePath) throws IOException {
    File file = new File(pomFile.getParent().getPath(), relativePath);
    assertTrue("file not found: " + relativePath, file.exists());
    return new String(FileUtil.loadFileText(file));
  }

  protected void assertResult(String relativePath, String content) throws IOException {
    assertResult(myProjectPom, relativePath, content);
  }

  protected void assertDirectory(String relativePath, TestFileSystemBuilder fileSystemBuilder) {
    fileSystemBuilder.build().assertDirectoryEqual(new File(myProjectPom.getParent().getPath(), relativePath));
  }

  protected void assertJar(String relativePath, TestFileSystemBuilder fileSystemBuilder) {
    fileSystemBuilder.build().assertFileEqual(new File(myProjectPom.getParent().getPath(), relativePath));
  }

  @Nullable
  protected static String extractJdkVersion(@NotNull Module module, boolean fallbackToInternal) {
    String jdkVersion = null;
    Optional<Sdk> sdk = Optional.ofNullable(ModuleRootManager.getInstance(module).getSdk());

    if (sdk.isEmpty()) {
      Optional<JdkOrderEntry> jdkEntry =
        Arrays.stream(ModuleRootManager.getInstance(module).getOrderEntries())
          .filter(JdkOrderEntry.class::isInstance)
          .map(JdkOrderEntry.class::cast)
          .findFirst();
      if (jdkEntry.isPresent()) {
        jdkVersion = jdkEntry.get().getJdkName();
      }
    }
    else {
      jdkVersion = sdk.get().getVersionString();
    }

    if (jdkVersion == null && fallbackToInternal) {
      jdkVersion = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk().getVersionString();
    }

    if (jdkVersion != null) {
      final int quoteIndex = jdkVersion.indexOf('"');
      if (quoteIndex != -1) {
        jdkVersion = jdkVersion.substring(quoteIndex + 1, jdkVersion.length() - 1);
      }
    }

    return jdkVersion;
  }

  protected void assertCopied(String path) {
    assertTrue(new File(myProjectPom.getParent().getPath(), path).exists());
  }

  protected void assertCopied(String path, String content) throws IOException {
    final File file = new File(myProjectPom.getParent().getPath(), path);
    assertTrue(file.exists());
    assertEquals(content, FileUtil.loadFile(file));
  }

  protected void assertNotCopied(String path) {
    assertFalse(new File(myProjectPom.getParent().getPath(), path).exists());
  }
}
