package de.plushnikov.intellij.plugin.extension.postfix;

import com.intellij.codeInsight.template.postfix.templates.BaseJavaPostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public final class LombokPostfixTemplateProvider extends BaseJavaPostfixTemplateProvider {

  private final Set<PostfixTemplate> lombokTemplates = new HashSet<>();

  public LombokPostfixTemplateProvider() {
    lombokTemplates.add(new LombokValPostfixTemplate());
    lombokTemplates.add(new LombokVarPostfixTemplate());
  }

  @Override
  public @NotNull Set<PostfixTemplate> getTemplates() {
    return lombokTemplates;
  }
}
