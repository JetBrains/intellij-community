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
package com.jetbrains.commandInterface.gnuCommandLine;

import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.gnuCommandLine.psi.CommandLineFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Provides quick help for arguments
 * @author Ilya.Kazakevich
 */
public final class CommandLineDocumentationProvider implements DocumentationProvider {
  @Nullable
  @Override
  public String getQuickNavigateInfo(final PsiElement element, final PsiElement originalElement) {
    return null;
  }

  @Nullable
  @Override
  public List<String> getUrlFor(final PsiElement element, final PsiElement originalElement) {
    return null;
  }

  @Nullable
  @Override
  public String generateDoc(final PsiElement element, @Nullable final PsiElement originalElement) {
    if (element instanceof CommandLineFile) {
      final Command command = ((CommandLineFile)element).findRealCommand();
      if (command != null) {
        return command.getHelp(false); // We do not need arguments info in help text
      }
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLookupItem(final PsiManager psiManager, final Object object, final PsiElement element) {
    return null;
  }

  @Nullable
  @Override
  public PsiElement getDocumentationElementForLink(final PsiManager psiManager, final String link, final PsiElement context) {
    return null;
  }
}
