/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
class JavaModuleFixtureBuilderImpl extends ModuleFixtureBuilderImpl implements JavaModuleFixtureBuilder {
  private List<Pair<String, String[]>> myLibraries = new ArrayList<Pair<String, String[]>>();
  private String myJdk;

  public JavaModuleFixtureBuilderImpl(final TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    super(ModuleType.JAVA, fixtureBuilder);
  }


  public JavaModuleFixtureBuilderImpl(final ModuleType moduleType, final TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    super(moduleType, fixtureBuilder);
  }

  public JavaModuleFixtureBuilder setLanguageLevel(LanguageLevel languageLevel) {
    throw new UnsupportedOperationException("setLanguageLevel is not implemented in : " + getClass());
  }

  public JavaModuleFixtureBuilder addLibrary(String libraryName, String[] classPath) {
    myLibraries.add(new Pair<String, String[]>(libraryName, classPath));
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

  protected ModuleFixtureImpl instantiateFixture() {
    return new ModuleFixtureImpl(this);
  }


  void initModule(final Module module) {
    super.initModule(module);


    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final LibraryTable libraryTable = model.getModuleLibraryTable();

    for (Pair<String, String[]> pair : myLibraries) {
      String libraryName = pair.first;
      String[] libs = pair.second;

      final Library library = libraryTable.createLibrary(libraryName);

      final Library.ModifiableModel libraryModel = library.getModifiableModel();
      for (String libraryPath : libs) {
        final VirtualFile root = JarFileSystem.getInstance().refreshAndFindFileByPath(libraryPath + "!/");
        assert root != null : libraryPath + " not found";
        libraryModel.addRoot(root, OrderRootType.CLASSES);
      }
      
      libraryModel.commit();
    }

    if (myJdk != null) {
      model.setJdk(JavaSdkImpl.getInstance().createJdk(module.getName() + "_jdk", myJdk, false));
    }

    model.commit();
  }
}
