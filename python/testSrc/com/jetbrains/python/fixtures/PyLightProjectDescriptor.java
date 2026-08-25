// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.PythonMockSdk;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Project descriptor (extracted from {@link com.jetbrains.python.fixtures.PyTestCase}) and should be used with it.
 * @author Ilya.Kazakevich
*/
public class PyLightProjectDescriptor extends LightProjectDescriptor {

  @Nullable
  private final String myName;

  @NotNull
  private final LanguageLevel myLevel;

  public PyLightProjectDescriptor(@NotNull LanguageLevel level) {
    this(null, level);
  }

  public PyLightProjectDescriptor(@NotNull String name) {
    this(name, LanguageLevel.getLatest());
  }

  private PyLightProjectDescriptor(@Nullable String name, @NotNull LanguageLevel level) {
    myName = name;
    myLevel = level;
  }

  @Override
  public Sdk getSdk() {
    return myName == null
           ? PythonMockSdk.create(myLevel)
           : PythonMockSdk.create(PythonTestUtil.getTestDataPath() + "/" + myName);
  }


  protected static void createLibrary(ModifiableRootModel model, final String name, final String path) {
    final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary(name).getModifiableModel();
    final VirtualFile home =
      LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManager.getHomePath() + path);

    modifiableModel.addRoot(home, OrderRootType.CLASSES);
    modifiableModel.commit();
  }
}
