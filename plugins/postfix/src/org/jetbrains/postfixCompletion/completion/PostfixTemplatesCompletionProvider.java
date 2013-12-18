package org.jetbrains.postfixCompletion.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.settings.PostfixCompletionSettings;
import org.jetbrains.postfixCompletion.templates.PostfixLiveTemplate;
import org.jetbrains.postfixCompletion.templates.PostfixTemplate;

import static org.jetbrains.postfixCompletion.completion.PostfixTemplateCompletionContributor.getPostfixLiveTemplate;

class PostfixTemplatesCompletionProvider extends CompletionProvider<CompletionParameters> {
  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
    if (!isCompletionEnabled(parameters)) {
      return;
    }

    PostfixLiveTemplate postfixLiveTemplate = getPostfixLiveTemplate(parameters.getOriginalFile(), parameters.getOffset());
    if (postfixLiveTemplate != null) {
      PsiFile file = parameters.getPosition().getContainingFile();
      final CustomTemplateCallback callback = new CustomTemplateCallback(parameters.getEditor(), file, false);
      String computedKey = postfixLiveTemplate.computeTemplateKey(callback);
      if (computedKey != null) {
        PostfixTemplate template = postfixLiveTemplate.getTemplateByKey(computedKey);
        if (template != null) {
          result = result.withPrefixMatcher(computedKey);
          result.addElement(new PostfixTemplateLookupElement(template, postfixLiveTemplate.getShortcut()));
        }
      }

      String possibleKey = postfixLiveTemplate.computeTemplateKeyWithoutContextChecking(parameters.getEditor());
      if (StringUtil.isNotEmpty(possibleKey)) {
        result = result.withPrefixMatcher(possibleKey);
        result.restartCompletionOnPrefixChange(StandardPatterns.string().startsWith(possibleKey));
      }
    }
  }

  private static boolean isCompletionEnabled(@NotNull CompletionParameters parameters) {
    if (!parameters.isAutoPopup()) {
      return false;
    }

    PostfixCompletionSettings settings = PostfixCompletionSettings.getInstance();
    if (settings == null || !settings.isPostfixPluginEnabled() || !settings.isTemplatesCompletionEnabled()) {
      return false;
    }

    return true;
  }
}
