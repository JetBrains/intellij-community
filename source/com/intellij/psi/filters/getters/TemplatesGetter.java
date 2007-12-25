package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.template.CompletionContextType;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ContextGetter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 28.04.2003
 * Time: 19:37:23
 * To change this template use Options | File Templates.
 */
public class TemplatesGetter implements ContextGetter{
  public Object[] get(PsiElement context, CompletionContext completionContext){
    final List result = new ArrayList();
    final TemplateSettings templateSettings = TemplateSettings.getInstance();
    final TemplateImpl[] templates = templateSettings.getTemplates();

    for (final TemplateImpl template : templates) {
      if (template.isDeactivated()) continue;

      final TemplateContext templateContext = template.getTemplateContext();

      if (!new CompletionContextType().isEnabled(templateContext)) {
        continue;
      }
      result.add(template);
    }

    return result.toArray();
  }
}
