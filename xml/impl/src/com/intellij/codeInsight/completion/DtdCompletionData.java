/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Oct 19, 2006
 * Time: 9:41:42 PM
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.position.LeftNeighbour;
import com.intellij.psi.filters.position.XmlTokenTypeFilter;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlEntityDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 05.06.2003
 * Time: 18:55:15
 * To change this template use Options | File Templates.
 */
public class DtdCompletionData extends CompletionData {
  public DtdCompletionData() {
    final LeftNeighbour entityFilter = new LeftNeighbour(new XmlTextFilter("%"));

    declareFinalScope(XmlToken.class);

    {
      final CompletionVariant variant = new CompletionVariant(
        new AndFilter(
          new LeftNeighbour(
            new OrFilter(
              new XmlTextFilter("#", "!", "(", ",", "|", "["),
              new XmlTokenTypeFilter(XmlTokenType.XML_NAME)
            )
          ),
          new NotFilter(entityFilter)
        )
      );
      variant.includeScopeClass(XmlToken.class, true);
      variant.addCompletion(
        new String[] {
          "#PCDATA","#IMPLIED","#REQUIRED","#FIXED","<!ATTLIST", "<!ELEMENT", "<!NOTATION", "INCLUDE", "IGNORE", "CDATA", "ID" , "IDREF", "EMPTY", "ANY",
          "IDREFS", "ENTITIES", "ENTITY", "<!ENTITY", "NMTOKEN", "NMTOKENS", "SYSTEM", "PUBLIC"
        },
        TailType.NONE
      );
      variant.setInsertHandler(new MyInsertHandler());
      registerVariant(variant);
    }

    {
      final CompletionVariant variant = new CompletionVariant(entityFilter);
      variant.includeScopeClass(XmlToken.class, true);
      variant.addCompletion(new DtdEntityGetter());
      variant.setInsertHandler(new XmlCompletionData.EntityRefInsertHandler());
      registerVariant(variant);
    }
  }

  @Override
  public String findPrefix(PsiElement insertedElement, int offset) {
    final PsiElement prevLeaf = PsiTreeUtil.prevLeaf(insertedElement);
    final PsiElement prevPrevLeaf = prevLeaf != null ? PsiTreeUtil.prevLeaf(prevLeaf):null;
    String prefix = super.findPrefix(insertedElement, offset);

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

  static class DtdEntityGetter implements ContextGetter {

    @Override
    public Object[] get(final PsiElement context, CompletionContext completionContext) {
      final List<String> results = new LinkedList<>();

      final PsiElementProcessor processor = new PsiElementProcessor() {
        @Override
        public boolean execute(@NotNull final PsiElement element) {
          if (element instanceof XmlEntityDecl) {
            final XmlEntityDecl xmlEntityDecl = (XmlEntityDecl)element;
            if (xmlEntityDecl.isInternalReference()) {
              results.add(xmlEntityDecl.getName());
            }
          }
          return true;
        }
      };

      XmlUtil.processXmlElements((XmlFile)context.getContainingFile().getOriginalFile(), processor, true);
      return ArrayUtil.toObjectArray(results);
    }
  }
  static class MyInsertHandler extends BasicInsertHandler {

    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
      super.handleInsert(context, item);

      if (item.getObject().toString().startsWith("<!")) {
        PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();

        int caretOffset = context.getEditor().getCaretModel().getOffset();
        PsiElement tag = PsiTreeUtil.getParentOfType(context.getFile().findElementAt(caretOffset), PsiNamedElement.class);

        if (tag == null) {
          context.getEditor().getDocument().insertString(caretOffset, " >");
          context.getEditor().getCaretModel().moveToOffset(caretOffset + 1);
        }
      }
    }
  }
}
