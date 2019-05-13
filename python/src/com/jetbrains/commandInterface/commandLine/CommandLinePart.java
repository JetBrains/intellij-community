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
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineFile;
import org.jetbrains.annotations.Nullable;

/**
 * Any part of commandline from file till any element
 *
 * @author Ilya.Kazakevich
 */
public interface CommandLinePart extends PsiElement {
  /**
   * @return command associated with this command line (if any)
   */
  @Nullable
  Command findRealCommand();

  /**
   * @return command line file where this part sits
   */
  @SuppressWarnings("ClassReferencesSubclass") // Although referencing child is bad idea, this hierarchy is coupled tightly and considered to be solid part
  @Nullable
  CommandLineFile getCommandLineFile();
}
