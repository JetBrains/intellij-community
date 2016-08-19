/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.console.completion;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;

/**
 * @author oleg
 * @date 10/26/10
 */
public class PythonConsoleAutopopupBlockingHandler extends TypedHandlerDelegate {

  public static final Key<Object> REPL_KEY = new Key<>("python.repl.console.editor");

  @Override
  public Result checkAutoPopup(final char charTyped, final Project project, final Editor editor, final PsiFile file) {
    if (editor.getUserData(REPL_KEY) != null){
      return Result.DEFAULT;
    }
    return Result.CONTINUE;
  }
}
