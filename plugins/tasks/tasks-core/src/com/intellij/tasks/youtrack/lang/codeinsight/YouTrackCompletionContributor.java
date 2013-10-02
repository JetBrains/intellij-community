package com.intellij.tasks.youtrack.lang.codeinsight;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.tasks.youtrack.YouTrackIntellisense;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.intellij.tasks.youtrack.YouTrackIntellisense.CompletionItem;

/**
 * @author Mikhail Golubev
 */
public class YouTrackCompletionContributor extends CompletionContributor {
  private static final Logger LOG = Logger.getInstance(YouTrackCompletionContributor.class);
  private static final int TIMEOUT = 2000;

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, CompletionResultSet result) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(DebugUtil.psiToString(parameters.getOriginalFile(), true));
    }

    super.fillCompletionVariants(parameters, result);

    PsiFile file = parameters.getOriginalFile();
    final YouTrackIntellisense intellisense = file.getUserData(YouTrackIntellisense.INTELLISENSE_KEY);
    if (intellisense == null) {
      return;
    }

    final Application application = ApplicationManager.getApplication();
    Future<List<CompletionItem>> future = application.executeOnPooledThread(new Callable<List<CompletionItem>>() {
      @Override
      public List<CompletionItem> call() throws Exception {
        return intellisense.fetchCompletion(parameters.getOriginalFile().getText(), parameters.getOffset());
      }
    });
    try {
      final List<CompletionItem> suggestions = future.get(TIMEOUT, TimeUnit.MILLISECONDS);
      result.addAllElements(ContainerUtil.map(suggestions, new Function<CompletionItem, LookupElement>() {
        @Override
        public LookupElement fun(CompletionItem item) {
          LookupElementBuilder builder = LookupElementBuilder.create(item.getOption())
            .withTypeText(item.getDescription(), true);
          // doesn't work actually TODO: write about it to guys in YouTrack
          //if (item.getStyleClass().equals("keyword")) {
          //  builder = builder.bold();
          //}
          return builder;
        }
      }));
    }
    catch (Exception ignored) {
      if (ignored instanceof TimeoutException) {
        LOG.debug("YouTrack request took more than %d ms to complete");
      }
      result.stopHere();
    }
  }
}
