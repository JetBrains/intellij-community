// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.tox;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAstElementGenerator;
import com.jetbrains.python.psi.PyElementGenerator;
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

  @Override
  public @NotNull List<Location> getLocation(final @NotNull String protocol,
                                             final @NotNull String path,
                                             final @NotNull Project project,
                                             final @NotNull GlobalSearchScope scope) {
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
  public static @Nullable String getEnvNameFromElement(final @NotNull PsiFile file) {
    if (!file.getName().equals(PyAstElementGenerator.getDummyFileName())) {
      return null;
    }
    if (!file.getText().equals(DUMMY_FILE_PADDING)) {
      return null;
    }
    return file.getUserData(ENV_NAME_KEY);
  }
}