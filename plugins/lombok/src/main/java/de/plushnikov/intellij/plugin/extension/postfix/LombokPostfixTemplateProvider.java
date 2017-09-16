package de.plushnikov.intellij.plugin.extension.postfix;

import com.intellij.codeInsight.template.postfix.templates.JavaPostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class LombokPostfixTemplateProvider extends JavaPostfixTemplateProvider {

  private final Set<PostfixTemplate> lombokTemplates = new HashSet<>();

  public LombokPostfixTemplateProvider() {
    lombokTemplates.add(new LombokValPostfixTemplate());
    lombokTemplates.add(new LombokVarPostfixTemplate());
  }

  @NotNull
  @Override
  public Set<PostfixTemplate> getTemplates() {
    return lombokTemplates;
  }

}
