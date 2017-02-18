/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  private static final InsertHandler<LookupElement> INSERT_HANDLER = new BasicInsertHandler<LookupElement>() {
    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
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
    extend(CompletionType.BASIC, psiElement(), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
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
