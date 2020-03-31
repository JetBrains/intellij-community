package org.intellij.lang.xpath.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import org.intellij.lang.xpath.XPathFile;
import org.jetbrains.annotations.Nullable;

public class XPathCharFilter extends CharFilter {
  @Nullable
  @Override
  public CharFilter.Result acceptChar(char c, int prefixLength, Lookup lookup) {
    if (c != '.' || !lookup.isCompletion()) {
      return null;
    }
    if (!(lookup.getPsiFile() instanceof XPathFile)) {
      // could be an XML file during XML autocompletion

      final LookupElement item = lookup.getCurrentItem();
      if (!(item instanceof AbstractLookup)) {
        // current completion item isn't something XPath related, continue with other handlers
        return null;
      }
    }
    // typed '.' should appear literally in editor, it has no meaning for completion
    return Result.HIDE_LOOKUP;
  }
}