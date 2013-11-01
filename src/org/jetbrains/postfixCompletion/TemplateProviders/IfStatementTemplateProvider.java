package org.jetbrains.postfixCompletion.TemplateProviders;

import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplateAcceptanceContext;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProvider;

@TemplateProvider(
  templateName = "if",
  description = "Checks boolean expression to be 'true'",
  example = "if (expr)")
public class IfStatementTemplateProvider extends TemplateProviderBase {
  @Override
  public void createItems(PostfixTemplateAcceptanceContext context) {

  }


}

