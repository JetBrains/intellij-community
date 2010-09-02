/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util.xml.ui.actions.generate;

import com.intellij.codeInsight.actions.SimpleCodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class CreateDomElementAction<T extends DomElement> extends SimpleCodeInsightAction {

  private final Class<T> myContextClass;

  public CreateDomElementAction(Class<T> contextClass) {
    myContextClass = contextClass;
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    DomElement element = createElement(getContextElement(editor), editor, file, project);
  }

  @Nullable
  protected abstract DomElement createElement(T context, Editor editor, PsiFile file, Project project);

  @Override
  protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    return getContextElement(editor) != null;
  }

  @Nullable
  protected T getContextElement(Editor editor) {
    return DomUtil.getContextElement(editor, myContextClass);
  }
}
