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
package com.jetbrains.commandInterface.commandLine;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parent of all command line elements (enables reference injection, see {@link #getReferences()})
 *
 * @author Ilya.Kazakevich
 */
public class CommandLineElement extends ASTWrapperPsiElement implements CommandLinePart {
  protected CommandLineElement(@NotNull final ASTNode node) {
    super(node);
  }


  @Nullable
  @Override
  public final CommandLineFile getCommandLineFile() {
    return PsiTreeUtil.getParentOfType(this, CommandLineFile.class);
  }

  @Nullable
  @Override
  public final Command findRealCommand() {
    final CommandLineFile commandLineFile = getCommandLineFile();
    if (commandLineFile != null) {
      return commandLineFile.findRealCommand();
    }
    return null;
  }

  @NotNull
  @Override
  public final PsiReference[] getReferences() {
    // We need it to enable reference injection
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }
}
