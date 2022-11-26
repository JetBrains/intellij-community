// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.template;

import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class PropertiesContextType extends TemplateContextType {

  public PropertiesContextType() {
    super(PropertiesBundle.message("properties.files.inspection.group.display.name"));
  }

  @Override
  public boolean isInContext(@NotNull TemplateActionContext templateActionContext) {
    final PsiFile file = templateActionContext.getFile();
    return file instanceof PropertiesFile && !(file.findElementAt(templateActionContext.getStartOffset()) instanceof PsiWhiteSpace);
  }

  @Override
  public @Nullable SyntaxHighlighter createHighlighter() {
    return SyntaxHighlighterFactory.getSyntaxHighlighter(PropertiesFileType.INSTANCE, null, null);
  }
}
