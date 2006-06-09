/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.ModuleFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mike
 */
abstract class ModuleFixtureBuilderImpl<T extends ModuleFixture> implements ModuleFixtureBuilder<T> {
  private static int ourIndex;

  private final List<String> myContentRoots = new ArrayList<String>();
  private final ModuleType myModuleType;
  private final TestFixtureBuilder<? extends IdeaProjectTestFixture> myFixtureBuilder;

  public ModuleFixtureBuilderImpl(@NotNull final ModuleType moduleType, TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
    myModuleType = moduleType;
    myFixtureBuilder = fixtureBuilder;
  }

  public ModuleFixtureBuilder<T> addContentRoot(final String contentRootPath) {
    myContentRoots.add(contentRootPath);
    return this;
  }

  protected Module createModule() {
    final Project project = myFixtureBuilder.getFixture().getProject();
    assert project != null;
    return ModuleManager.getInstance(project).newModule(getNextIndex() + ".iml", myModuleType);
  }

  private static int getNextIndex() {
    return ourIndex++;
  }


  public T getFixture() {
    return instantiateFixture();
  }

  protected abstract T instantiateFixture();

  Module buildModule() {
    final Module[] module = new Module[]{null};

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        module[0] = createModule();
        initModule(module[0]);
      }
    });

    return module[0];
  }

  void initModule(Module module) {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = rootManager.getModifiableModel();

    for (String contentRoot : myContentRoots) {
      final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(contentRoot);
      assert virtualFile != null : "cannot find content root: " + contentRoot;
      rootModel.addContentEntry(virtualFile);
    }

    rootModel.commit();
  }
}
