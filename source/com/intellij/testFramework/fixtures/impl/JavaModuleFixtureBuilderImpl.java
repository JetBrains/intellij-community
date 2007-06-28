/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.ModuleFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * @author mike
 */
abstract class JavaModuleFixtureBuilderImpl<T extends ModuleFixture> extends ModuleFixtureBuilderImpl<T> implements JavaModuleFixtureBuilder<T> {
  private List<Lib> myLibraries = new ArrayList<Lib>();
  private String myJdk;
  private MockJdkLevel myMockJdkLevel = MockJdkLevel.jdk14;

  public JavaModuleFixtureBuilderImpl(final TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    super(ModuleType.JAVA, fixtureBuilder);
  }

  public JavaModuleFixtureBuilderImpl(final ModuleType moduleType, final TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    super(moduleType, fixtureBuilder);
  }

  public JavaModuleFixtureBuilder setLanguageLevel(LanguageLevel languageLevel) {
    throw new UnsupportedOperationException("setLanguageLevel is not implemented in : " + getClass());
  }

  public JavaModuleFixtureBuilder addLibrary(String libraryName, String... classPath) {
    final HashMap<OrderRootType, String[]> map = new HashMap<OrderRootType, String[]>();
    map.put(OrderRootType.CLASSES, classPath);
    myLibraries.add(new Lib(libraryName, map));
    return this;
  }

  public JavaModuleFixtureBuilder addLibrary(@NonNls final String libraryName, final Map<OrderRootType, String[]> roots) {
    myLibraries.add(new Lib(libraryName, roots));
    return this;
  }

  public JavaModuleFixtureBuilder addLibraryJars(String libraryName, String basePath, String... jars) {
    String[] classPath = new String[jars.length];
    for (int i = 0; i < jars.length; i++) {
      classPath[i] = basePath + jars[i];
    }
    return addLibrary(libraryName, classPath);
  }

  public JavaModuleFixtureBuilder addJdk(String jdkPath) {
    myJdk = jdkPath;
    return this;
  }

  public void setMockJdkLevel(final MockJdkLevel level) {
    myMockJdkLevel = level;
  }

  void initModule(final Module module) {
    super.initModule(module);

    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);

    if (myMockJdkLevel == MockJdkLevel.jdk15) {
      moduleRootManager.setLanguageLevel(LanguageLevel.JDK_1_5);
    }

    final ModifiableRootModel model = moduleRootManager.getModifiableModel();
    final LibraryTable libraryTable = model.getModuleLibraryTable();

    for (Lib lib : myLibraries) {
      String libraryName = lib.getName();

      final Library library = libraryTable.createLibrary(libraryName);

      final Library.ModifiableModel libraryModel = library.getModifiableModel();

      for (OrderRootType rootType : OrderRootType.ALL_TYPES) {
        final String[] roots = lib.getRoots(rootType);
        for (String root : roots) {
          final VirtualFile vRoot = OrderRootType.CLASSES.equals(rootType)
                                    ? JarFileSystem.getInstance().refreshAndFindFileByPath(root + "!/")
                                    : LocalFileSystem.getInstance().refreshAndFindFileByPath(root);
          if (vRoot != null) {
            libraryModel.addRoot(vRoot, rootType);
          }
        }
      }
      libraryModel.commit();
    }

    if (myJdk != null) {
      model.setJdk(JavaSdkImpl.getInstance().createJdk(module.getName() + "_jdk", myJdk, false));
    } else {
      final ProjectJdk projectJdk;
      switch (myMockJdkLevel) {
        default:
          projectJdk = JavaSdkImpl.getMockJdk("java 1.4");
          break;
        case jdk15:
          projectJdk = JavaSdkImpl.getMockJdk15("java 1.5");
          break;
      }
      model.setJdk(projectJdk);
    }
    model.commit();
  }

  private static class Lib {
    private String myName;
    private Map<OrderRootType, String []> myRoots;

    public Lib(final String name, final Map<OrderRootType, String[]> roots) {
      myName = name;
      myRoots = roots;
    }

    public String getName() {
      return myName;
    }

    public String [] getRoots(OrderRootType rootType) {
      final String[] roots = myRoots.get(rootType);
      return roots != null ? roots : new String[0];
    }
  }
}
