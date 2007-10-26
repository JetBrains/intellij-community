/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package org.jetbrains.idea.maven;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.events.MavenEventsHandler;
import org.jetbrains.idea.maven.navigator.PomTreeStructure;
import org.jetbrains.idea.maven.navigator.PomTreeViewSettings;
import org.jetbrains.idea.maven.project.MavenImportProcessor;
import org.jetbrains.idea.maven.repo.MavenRepository;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public abstract class ProjectImportingTest extends IdeaTestCase {
  private VirtualFile root;
  private VirtualFile projectPom;
  private List<VirtualFile> poms = new ArrayList<VirtualFile>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          File dir = createTempDirectory();
          root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @Override
  protected void setUpModule() {
  }

  protected PomTreeStructure.RootNode createMavenTree() {
    PomTreeStructure s = new PomTreeStructure(myProject, myProject.getComponent(MavenProjectsState.class),
                                              myProject.getComponent(MavenRepository.class),
                                              myProject.getComponent(MavenEventsHandler.class)) {
      {
        for (VirtualFile pom : poms) {
          this.root.addUnder(new PomNode(pom));
        }
      }

      protected PomTreeViewSettings getTreeViewSettings() {
        return new PomTreeViewSettings();
      }

      protected void updateTreeFrom(@Nullable SimpleNode node) {
      }
    };
    return (PomTreeStructure.RootNode)s.getRootElement();
  }

  protected void assertModules(String... expectedNames) {
    Module[] actual = ModuleManager.getInstance(myProject).getModules();
    List<String> actualNames = new ArrayList<String>();

    for (Module m : actual) {
      actualNames.add(m.getName());
    }

    assertEquals(expectedNames.length, actualNames.size());
    assertElementsAreEqual(expectedNames, actualNames);
  }

  protected void assertModuleLibraries(String moduleName, String... expectedLibraries) {
    Module m = getModule(moduleName);
    List<String> actual = new ArrayList<String>();

    for (OrderEntry e : ModuleRootManager.getInstance(m).getOrderEntries()) {
      if (e instanceof LibraryOrderEntry) {
        actual.add(e.getPresentableName());
      }
    }

    assertElementsAreEqual(expectedLibraries, actual);
  }

  private void assertElementsAreEqual(final String[] names, final List<String> actualNames) {
    for (String name : names) {
      String s = "\nexpected: " + Arrays.asList(names) + "\nactual: " + actualNames;
      assertTrue(s, actualNames.contains(name));
    }
  }

  protected Module getModule(String name) {
    return ModuleManager.getInstance(myProject).findModuleByName(name);
  }

  protected void createProjectPom(String xml) throws IOException {
    projectPom = createPomFile(root, xml);
  }

  protected void createModulePom(String relativePath, String xml) throws IOException {
    File externalDir = new File(root.getPath(), relativePath);
    externalDir.mkdirs();

    VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(externalDir);
    createPomFile(dir, xml);
  }

  private VirtualFile createPomFile(VirtualFile dir, String xml) throws IOException {
    VirtualFile f = dir.createChildData(null, "pom.xml");
    f.setBinaryContent(createValidPom(xml).getBytes());
    poms.add(f);
    return f;
  }

  private String createValidPom(String xml) {
    return "<?xml version=\"1.0\"?>" +
           "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"" +
           "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">" +
           "  <modelVersion>4.0.0</modelVersion>" +
           xml +
           "</project>";
  }

  protected void importProject(String xml) throws IOException {
    createProjectPom(xml);
    importProject();
  }

  protected void importProject() {
    ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
    files.add(projectPom);
    ArrayList<String> profiles = new ArrayList<String>();

    MavenImportProcessor p = new MavenImportProcessor(myProject);
    p.createMavenProjectModel(new HashMap<VirtualFile, Module>(), files, profiles);
    p.createMavenToIdeaMapping(false);
    p.resolve(myProject, profiles);
    p.commit(myProject, profiles, false);
  }
}
