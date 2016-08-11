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
package com.intellij.util.xml.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.actions.generate.DomTemplateRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * User: Sergey.Vasiliev
 */
public class DomTemplateRunnerImpl extends DomTemplateRunner {
  private final Project myProject;

  public DomTemplateRunnerImpl(Project project) {
    myProject = project;
  }

  @Override
  public <T extends DomElement> void runTemplate(final T t, final String mappingId, final Editor editor) {
       runTemplate(t, mappingId, editor, new HashMap<>());
  }

  @Override
  public <T extends DomElement> void runTemplate(T t, String mappingId, Editor editor,
                                                 @NotNull Map<String, String> predefinedVars) {
    final Template template = getTemplate(mappingId);
    runTemplate(t, editor, template, predefinedVars);
  }

  public <T extends DomElement> void runTemplate(final T t, final Editor editor, @Nullable final Template template) {
     runTemplate(t, editor, template, new HashMap<>());
  }

  public <T extends DomElement> void runTemplate(final T t, final Editor editor, @Nullable final Template template, Map<String, String> predefinedVars) {
    if (template != null) {
      DomElement copy = t.createStableCopy();
      PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(editor.getDocument());
      XmlTag tag = copy.getXmlTag();
      assert tag != null;
      editor.getCaretModel().moveToOffset(tag.getTextRange().getStartOffset());
      copy.undefine();

      PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(editor.getDocument());

      template.setToReformat(true);
      TemplateManager.getInstance(myProject).startTemplate(editor, template, true, predefinedVars, null);
    }
  }

  @Nullable
  protected static Template getTemplate(final String mappingId) {
    return mappingId != null ? TemplateSettings.getInstance().getTemplateById(mappingId) : null;
  }
}
