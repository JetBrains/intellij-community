/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
           ? PythonMockSdk.create(myLevel, getAdditionalRoots())
           : PythonMockSdk.create(PythonTestUtil.getTestDataPath() + "/" + myName);
  }

  /**
   * @return additional roots to add to mock python
   * @apiNote ignored when name is provided.
   */
  protected VirtualFile @NotNull [] getAdditionalRoots() {
    return VirtualFile.EMPTY_ARRAY;
  }

  protected void createLibrary(ModifiableRootModel model, final String name, final String path) {
    final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary(name).getModifiableModel();
    final VirtualFile home =
      LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManager.getHomePath() + path);

    modifiableModel.addRoot(home, OrderRootType.CLASSES);
    modifiableModel.commit();
  }
}
