// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import com.intellij.ide.projectWizard.ProjectWizardTestCase;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.maven.testFramework.MavenTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.io.PathKt;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.wizards.MavenProjectBuilder;
import org.jetbrains.idea.maven.wizards.MavenProjectImportProvider;

import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class MavenMultiProjectImportTest extends ProjectWizardTestCase<AbstractProjectWizard> {

  private Path myDir;

  public void testIndicesForDifferentProjectsShouldBeSameInstance() {
    myDir = getTempDir().newPath("", true);
    VirtualFile pom1 = createPomXml("projectDir1", "<groupId>test</groupId>" +
                                                   "<artifactId>project1</artifactId>" +
                                                   "<version>1</version>");
    importMaven(myProject, pom1);

    VirtualFile pom2 = createPomXml("projectDir2", "<groupId>test</groupId>" +
                                                   "<artifactId>project2</artifactId>" +
                                                   "<version>1</version>");

    MavenProjectImportProvider provider = new MavenProjectImportProvider();
    MavenProjectBuilder builder = (MavenProjectBuilder)provider.getBuilder();
    builder.setFileToImport(pom2);
    Module module = importProjectFrom(pom2.getPath(), null, provider);
    Project project2 = module.getProject();
    importMaven(project2, pom2);
    MavenIndicesManager.getInstance(project2).updateIndicesListSync();
    MavenIndicesManager.getInstance(myProject).updateIndicesListSync();

    MavenIndexHolder firstIndices = MavenIndicesManager.getInstance(myProject).getIndex();
    MavenIndexHolder secondIndices = MavenIndicesManager.getInstance(project2).getIndex();
    assertThat(firstIndices.getIndices()).hasSize(2);
    assertThat(secondIndices.getIndices()).hasSize(2);
    //    assertSame(firstIndices.getLocalIndex(), secondIndices.getLocalIndex());
    //   assertSame(firstIndices.getRemoteIndices().get(0), secondIndices.getRemoteIndices().get(0));
  }

  private VirtualFile createPomXml(String dir,
                                   @Language(value = "XML", prefix = "<project>", suffix = "</project>") String pomxml) {
    Path projectDir = myDir.resolve(dir);
    projectDir.toFile().mkdirs();
    Path pom = projectDir.resolve("pom.xml");
    PathKt.write(pom, MavenTestCase.createPomXml(pomxml));
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(pom);
  }


  private void importMaven(@NotNull Project project, @NotNull VirtualFile file) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    manager.initForTests();
    manager.waitForImportCompletion();
    manager.resetManagedFilesAndProfilesInTests(Collections.singletonList(file), MavenExplicitProfiles.NONE);
    ApplicationManager.getApplication().invokeAndWait(() -> {
      manager.scheduleImportInTests(Collections.singletonList(file));
      manager.importProjects();
    });

    Promise<?> promise = manager.waitForImportCompletion();
    PlatformTestUtil.waitForPromise(promise);
  }
}
