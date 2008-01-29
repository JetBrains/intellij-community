package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.patterns.MatchingContext;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.QueryResultSet;
import com.intellij.xml.XmlExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class XmlCompletionContributor extends CompletionContributor{
  public void registerCompletionProviders(final CompletionRegistrar registrar) {
    registrar.extendClassNameCompletion(PlatformPatterns.psiElement(XmlTokenType.XML_NAME)).dependent("TagNameCompletion",
                                                                                                      LegacyCompletionContributor.LEGACY).withProvider(new CompletionProvider<LookupElement, CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final MatchingContext matchingContext, @NotNull final QueryResultSet<LookupElement> result) {
        CompletionContext context = parameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        final String prefix = context.getPrefix();
        final int pos = prefix.indexOf(':');

        if (pos >= 0) {
          context.setPrefix(prefix.substring(pos + 1));
        }
        final String namespacePrefix = pos > 0 ? prefix.substring(0, pos) : null;
        result.clear();
        final XmlFile file = (XmlFile)context.file;

        final Set<String> names = XmlExtension.getExtension(file).getAvailableTagNames(file);
        if (names.isEmpty()) {
          return;
        }
        for (String tagName : names) {
          final LookupItem item = new LookupItem<String>(tagName, tagName);
          final XmlTagInsertHandler insertHandler = new ExtendedTagInsertHandler(tagName, namespacePrefix);
          item.setAttribute(LookupItem.INSERT_HANDLER_ATTR, insertHandler);
          if (context.prefixMatches(tagName)) {
            result.addElement(item);
          }
        }

      }
    });
  }

}
