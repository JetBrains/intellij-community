// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.impl;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.actions.generate.DomTemplateRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

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

  public <T extends DomElement> void runTemplate(final T t, final Editor editor, final @Nullable Template template) {
     runTemplate(t, editor, template, new HashMap<>());
  }

  public <T extends DomElement> void runTemplate(final T t, final Editor editor, final @Nullable Template template, Map<String, String> predefinedVars) {
    if (template != null && t != null) {
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

  protected static @Nullable Template getTemplate(final String mappingId) {
    return mappingId != null ? TemplateSettings.getInstance().getTemplateById(mappingId) : null;
  }
}
