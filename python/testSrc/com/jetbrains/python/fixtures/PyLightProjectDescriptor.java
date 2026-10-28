// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.fixtures;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.python.sdk.backend.PyInterpreterLibraryFlowKt;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.jetbrains.python.tools.sdkTools.PythonMockSdk;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.internal.PySdkNamesKt;
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
    super(true);
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

  /** A Python module, so the module is a Python project and its interpreter is in the project structure. */
  @Override
  public @NotNull String getModuleTypeId() {
    return PySdkNamesKt.PYTHON_MODULE_ID;
  }

  /**
   * The SDK of the module in the legacy flow: the platform adds it to the SDK table, writes it to the module, and removes
   * it with the light project, as in older builds. The interpreter library flow gives the module an interpreter instead,
   * see {@link #setUpProject}, so the platform gets no SDK.
   */
  @Override
  public final Sdk getSdk() {
    return PyInterpreterLibraryFlowKt.isInterpreterLibraryFlowEnabled() ? null : createMockSdk();
  }

  /** The mock SDK of the interpreter of the module, see {@link #setUpProject}. */
  protected @NotNull Sdk createMockSdk() {
    return myName == null
           ? PythonMockSdk.create(myLevel)
           : PythonMockSdk.create(PythonTestUtil.getTestDataPath() + "/" + myName);
  }

  /**
   * In the interpreter library flow, gives the Python project of the module an interpreter of {@link #createMockSdk()}.
   * The registry holds it, owned by the Python project, and the SDK is in no SDK table. The interpreter lives as long as
   * the light project. The legacy flow gets its SDK from {@link #getSdk()}.
   */
  @Override
  public void setUpProject(@NotNull Project project, @NotNull SetupHandler handler) throws Exception {
    super.setUpProject(project, handler);
    if (!PyInterpreterLibraryFlowKt.isInterpreterLibraryFlowEnabled()) return;
    Module module = ModuleManager.getInstance(project).findModuleByName(TEST_MODULE_NAME);
    if (module != null) {
      PyProjectStructureTestUtilKt.addMockPythonInterpreterForTests(module, createMockSdk());
    }
  }


  protected static void createLibrary(ModifiableRootModel model, final String name, final String path) {
    final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary(name).getModifiableModel();
    final VirtualFile home =
      StandardFileSystems.local().refreshAndFindFileByPath(PathManager.getHomePath() + path);

    modifiableModel.addRoot(home, OrderRootType.CLASSES);
    modifiableModel.commit();
  }
}
