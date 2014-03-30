/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.tagTreeHighlighting;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory;
import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlTagTreeHighlightingPassFactory extends AbstractProjectComponent implements TextEditorHighlightingPassFactory {

  public XmlTagTreeHighlightingPassFactory(Project project, TextEditorHighlightingPassRegistrar registrar) {
    super(project);
    registrar.registerTextEditorHighlightingPass(this, new int[]{Pass.UPDATE_ALL}, null, false, -1);
  }

  @Override
  public TextEditorHighlightingPass createHighlightingPass(@NotNull final PsiFile file, @NotNull final Editor editor) {
    if (editor.isOneLineMode()) return null;

    if (!XmlTagTreeHighlightingUtil.isTagTreeHighlightingActive(file)) return null;
    if (!(editor instanceof EditorEx)) return null;

    return new XmlTagTreeHighlightingPass(file, (EditorEx)editor);
  }
}

