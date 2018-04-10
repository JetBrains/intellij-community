/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.testing.tox;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.impl.PyElementGeneratorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Enables env-based rerun
 * @author Ilya.Kazakevich
 */
public final class PyToxTestLocator implements SMTestLocator {
  public static final PyToxTestLocator INSTANCE = new PyToxTestLocator();

  private static final String DUMMY_FILE_PADDING = "#env";
  private static final Key<String> ENV_NAME_KEY = Key.create("ENV_NAME");
  static final String PROTOCOL_ID = "tox_env";

  @NotNull
  @Override
  public List<Location> getLocation(@NotNull final String protocol,
                                    @NotNull final String path,
                                    @NotNull final Project project,
                                    @NotNull final GlobalSearchScope scope) {
    final PsiFile file = PyElementGenerator.getInstance(project).createDummyFile(LanguageLevel.PYTHON27, DUMMY_FILE_PADDING);
    file.putUserData(ENV_NAME_KEY, path);
    @SuppressWarnings("unchecked")
    final List<Location> locations = Collections.singletonList(new PsiLocation(file));
    return locations;
  }

  /**
   * @param file dummy file to which env is resolved
   * @return env name of dummy file or null if different file
   */
  @Nullable
  public static String getEnvNameFromElement(@NotNull final PsiFile file) {
    if (!file.getName().equals(PyElementGeneratorImpl.getDummyFileName())) {
      return null;
    }
    if (! file.getText().equals(DUMMY_FILE_PADDING)) {
      return null;
    }
    return file.getUserData(ENV_NAME_KEY);
  }
}