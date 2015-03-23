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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parent of all  references injected to command line elements
 *
 * @author Ilya.Kazakevich
 */
abstract class CommandLineElementReference<T extends PsiElement> extends PsiReferenceBase<T> {
  protected CommandLineElementReference(@NotNull final T element) {
    super(element);
  }

  /**
   * @return command line file this element sits in (if any)
   */
  @Nullable
  protected final CommandLineFile getCommandLineFile() {
    return PsiTreeUtil.getParentOfType(getElement(), CommandLineFile.class);
  }

  /**
   * @return command line validation result (if any)
   */
  @Nullable
  protected final ValidationResult getValidationResult() {
    final CommandLineFile file = getCommandLineFile();
    if (file == null) {
      return null;
    }
    return file.getValidationResult();
  }

  /**
   * @return real command used in parent file (if any)
   */
  @Nullable
  protected final Command getCommand() {
    final CommandLineFile file = getCommandLineFile();
    if (file == null) {
      return null;
    }
    return file.findRealCommand();
  }
}
