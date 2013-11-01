package org.jetbrains.postfixCompletion.TemplateProviders;

import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplateAcceptanceContext;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProvider;

@TemplateProvider(
  templateName = "var",
  description = "Introduces variable for expression",
  example = "var x = expr;")
public class IntroduceVariableTemplateProvider extends TemplateProviderBase {
  @Override
  public void createItems(PostfixTemplateAcceptanceContext context) {

  }
}
