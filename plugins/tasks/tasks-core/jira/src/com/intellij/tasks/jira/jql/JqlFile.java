// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira.jql;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlFile extends PsiFileBase {
  public JqlFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, JqlLanguage.INSTANCE);
  }

  @Override
  public @NotNull FileType getFileType() {
    return JqlFileType.INSTANCE;
  }
}
