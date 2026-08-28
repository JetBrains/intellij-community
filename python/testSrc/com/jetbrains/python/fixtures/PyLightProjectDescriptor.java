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
import com.jetbrains.python.tools.sdkTools.PythonMockSdk;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Project descriptor (extracted from {@link com.jetbrains.python.fixtures.PyTestCase}) and should be used with it.
 * The project is usually cached and reused, unless {@link #myName} or {@link #myLevel} differ, see parent contract.
 *
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

  protected PyLightProjectDescriptor(@Nullable String name, @NotNull LanguageLevel level) {
    myName = name;
    myLevel = level;
  }

  @Override
  public final boolean equals(Object o) {
    // Inheritors should never be considered equal because they might override module configuration functions
    if (o == null || getClass() != o.getClass()) return false;
    PyLightProjectDescriptor that = (PyLightProjectDescriptor)o;
    return Objects.equals(myName, that.myName) && myLevel == that.myLevel;
  }

  @Override
  public final int hashCode() {
    return Objects.hash(myName, myLevel, getClass());
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
