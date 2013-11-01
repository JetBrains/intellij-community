package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplateAcceptanceContext;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProvider;

import java.util.List;

@TemplateProvider(
  templateName = "var",
  description = "Introduces variable for expression",
  example = "var x = expr;")
public class IntroduceVariableTemplateProvider extends TemplateProviderBase {
  @Override
  public void createItems(PostfixTemplateAcceptanceContext context, List<LookupElement> consumer) {

  }
}
