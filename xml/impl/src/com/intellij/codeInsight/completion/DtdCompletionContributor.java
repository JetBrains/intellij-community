// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class DtdCompletionContributor extends CompletionContributor {
  private static final String[] KEYWORDS = new String[] {
    "#PCDATA","#IMPLIED","#REQUIRED","#FIXED","<!ATTLIST", "<!ELEMENT", "<!NOTATION", "INCLUDE",
    "IGNORE", "CDATA", "ID" , "IDREF", "EMPTY", "ANY", "IDREFS", "ENTITIES", "ENTITY", "<!ENTITY",
    "NMTOKEN", "NMTOKENS", "SYSTEM", "PUBLIC"
  };

  private static final InsertHandler<LookupElement> INSERT_HANDLER = new BasicInsertHandler<>() {
    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
      super.handleInsert(context, item);

      if (item.getObject().toString().startsWith("<!")) {
        context.commitDocument();

        int caretOffset = context.getEditor().getCaretModel().getOffset();
        PsiElement tag = PsiTreeUtil.getParentOfType(context.getFile().findElementAt(caretOffset), PsiNamedElement.class);

        if (tag == null) {
          context.getEditor().getDocument().insertString(caretOffset, " >");
          context.getEditor().getCaretModel().moveToOffset(caretOffset + 1);
        }
      }
    }
  };

  public DtdCompletionContributor() {
    extend(CompletionType.BASIC, psiElement(), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        PsiElement prev = PsiTreeUtil.prevVisibleLeaf(position);
        if (prev != null && hasDtdKeywordCompletion(prev)) {
          addKeywordCompletions(result.withPrefixMatcher(keywordPrefix(position, result.getPrefixMatcher().getPrefix())));
        }
        if (prev != null && prev.textMatches("%")) {
          addEntityCompletions(result, position);
        }
      }
    });
  }

  @NotNull
  private static String keywordPrefix(@NotNull PsiElement position, @NotNull String prefix) {
    final PsiElement prevLeaf = PsiTreeUtil.prevLeaf(position);
    final PsiElement prevPrevLeaf = prevLeaf != null ? PsiTreeUtil.prevLeaf(prevLeaf):null;

    if (prevLeaf != null) {
      final String prevLeafText = prevLeaf.getText();

      if("#".equals(prevLeafText)) {
        prefix = "#" + prefix;
      } else if ("!".equals(prevLeafText) && prevPrevLeaf != null && "<".equals(prevPrevLeaf.getText())) {
        prefix = "<!" + prefix;
      }
    }

    return prefix;

  }

  private static void addKeywordCompletions(@NotNull CompletionResultSet result) {
    for (String keyword : KEYWORDS) {
      result.addElement(LookupElementBuilder.create(keyword).withInsertHandler(INSERT_HANDLER));
    }
  }

  private static void addEntityCompletions(@NotNull final CompletionResultSet result, PsiElement position) {
    final PsiElementProcessor processor = new PsiElementProcessor() {
      @Override
      public boolean execute(@NotNull final PsiElement element) {
        if (element instanceof XmlEntityDecl) {
          final XmlEntityDecl xmlEntityDecl = (XmlEntityDecl)element;
          String name = xmlEntityDecl.getName();
          if (name != null && xmlEntityDecl.isInternalReference()) {
            result.addElement(LookupElementBuilder.create(name).withInsertHandler(XmlCompletionContributor.ENTITY_INSERT_HANDLER));
          }
        }
        return true;
      }
    };
    XmlUtil.processXmlElements((XmlFile)position.getContainingFile().getOriginalFile(), processor, true);
  }

  private static boolean hasDtdKeywordCompletion(@NotNull PsiElement prev) {
    return prev.textMatches("#") ||
           prev.textMatches("!") ||
           prev.textMatches("(") ||
           prev.textMatches(",") ||
           prev.textMatches("|") ||
           prev.textMatches("[") ||
           prev.getNode().getElementType() == XmlTokenType.XML_NAME;
  }
}
