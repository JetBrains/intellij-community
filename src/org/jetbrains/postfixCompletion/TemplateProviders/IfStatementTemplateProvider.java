package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplateAcceptanceContext;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProvider;

import java.util.List;

@TemplateProvider(
  templateName = "if",
  description = "Checks boolean expression to be 'true'",
  example = "if (expr)")
public class IfStatementTemplateProvider extends TemplateProviderBase {
  @Override
  public void createItems(PostfixTemplateAcceptanceContext context, List<LookupElement> consumer) {

  }
}

