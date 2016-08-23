/*
 * Copyright 2005-2008 Sascha Weinreuter
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

package org.intellij.lang.xpath.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.patterns.PatternCondition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.psi.*;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;
import java.util.Collection;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
*/
public class XPathCompletionContributor extends CompletionContributor {
  public static final XPathInsertHandler INSERT_HANDLER = new XPathInsertHandler();

  public XPathCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().withParent(XPathNodeTest.class), new CompletionProvider<CompletionParameters>() {
      protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
        final XPathNodeTest nodeTest = (XPathNodeTest)parameters.getPosition().getParent();
        addResult(result, CompletionLists.getNodeTestCompletions(nodeTest), parameters.getPosition(), parameters.getOffset());
      }
    });
    extend(CompletionType.BASIC, psiElement().withParent(psiElement(XPathNodeTest.class).with(prefix())), new CompletionProvider<CompletionParameters>() {
      protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
        final XPathNodeTest nodeTest = (XPathNodeTest)parameters.getPosition().getParent();
        addResult(result, CompletionLists.getFunctionCompletions(nodeTest), parameters.getPosition(), parameters.getOffset());
      }
    });

    extend(CompletionType.BASIC, psiElement().withParent(XPathAxisSpecifier.class), new CompletionProvider<CompletionParameters>() {
      protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
        addResult(result, CompletionLists.getAxisCompletions(), parameters.getPosition(), parameters.getOffset());
      }
    });

    extend(CompletionType.BASIC, psiElement().withParent(XPathFunctionCall.class), new CompletionProvider<CompletionParameters>() {
      protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
        final XPathFunctionCall call = (XPathFunctionCall)parameters.getPosition().getParent();
        addResult(result, CompletionLists.getFunctionCompletions(call), parameters.getPosition(), parameters.getOffset());
      }
    });
    extend(CompletionType.BASIC, psiElement().withParent(psiElement(XPathFunctionCall.class).without(prefix())), new CompletionProvider<CompletionParameters>() {
      protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
        final XPathFunctionCall call = (XPathFunctionCall)parameters.getPosition().getParent();
        addResult(result, CompletionLists.getNodeTypeCompletions(call), parameters.getPosition(), parameters.getOffset());
      }
    });

    extend(CompletionType.BASIC, psiElement().withParent(XPathVariableReference.class), new CompletionProvider<CompletionParameters>() {
      protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
        addResult(result, CompletionLists.getVariableCompletions((XPathVariableReference)parameters.getPosition().getParent()), parameters.getPosition(), parameters.getOffset());
      }
    });

    extend(CompletionType.BASIC, psiElement().withParent(psiElement(XPath2TypeElement.class).without(prefix())), new CompletionProvider<CompletionParameters>() {
      protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
        final XPathElement parent = PsiTreeUtil.getParentOfType(parameters.getPosition(), XPathElement.class);
        assert parent != null;

        if (parent.getParent() instanceof XPath2TreatAs || parent.getParent() instanceof XPath2InstanceOf) {
          addResult(result, CompletionLists.getNodeTypeCompletions(parent), parameters.getPosition(), parameters.getOffset());
        }

        final NamespaceContext namespaceContext = parent.getXPathContext().getNamespaceContext();
        if (namespaceContext != null) {
          final String prefixForURI = namespaceContext.getPrefixForURI(XPath2Type.XMLSCHEMA_NS, parent.getXPathContext().getContextElement());
          if (prefixForURI != null && prefixForURI.length() > 0) {
            addResult(result, ContainerUtil.map(XPath2Type.SchemaType.listSchemaTypes(), type -> new MyLookup(prefixForURI + ":" + type.getQName().getLocalPart())), parameters.getPosition(), parameters.getOffset());
          }
        }
      }
    });
    extend(CompletionType.BASIC, psiElement().withParent(psiElement(XPath2TypeElement.class).with(prefix())), new CompletionProvider<CompletionParameters>() {
      protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
        final XPath2TypeElement parent = PsiTreeUtil.getParentOfType(parameters.getPosition(), XPath2TypeElement.class);
        assert parent != null;

        final QName qName = parent.getXPathContext().getQName(parent);
        if (qName != null && qName.getNamespaceURI().equals(XPath2Type.XMLSCHEMA_NS)) {
          addResult(result, ContainerUtil.map(XPath2Type.SchemaType.listSchemaTypes(), type -> new MyLookup(type.getQName().getLocalPart())), parameters.getPosition(), parameters.getOffset());
        }
      }
    });
  }

  private static PatternCondition<QNameElement> prefix() {
    return new PatternCondition<QNameElement>("hasPrefix") {
      @Override
      public boolean accepts(@NotNull QNameElement qnameElement, ProcessingContext context) {
        final PrefixedName qname = qnameElement.getQName();
        return qname != null && qname.getPrefix() != null;
      }
    };
  }

  private static void addResult(CompletionResultSet result, Collection<Lookup> collection, PsiElement position, int offset) {
    result = result.withPrefixMatcher(findPrefixStatic(position, offset));

    for (Lookup lookup : collection) {
      final LookupItem<Lookup> item = new LookupItem<>(lookup, lookup.toString());
      item.setInsertHandler(INSERT_HANDLER);
      if (lookup.isKeyword()) {
        item.setBold();
      }
      result.addElement(item);
    }
  }

  private static String findPrefixStatic(PsiElement element, int i) {
    String prefix = CompletionData.findPrefixStatic(element, i);

    if (element.getParent() instanceof XPathVariableReference) {
      prefix = "$" + prefix;
    }

    if (element.getParent() instanceof XPathNodeTest) {
      final XPathNodeTest nodeTest = ((XPathNodeTest)element.getParent());
      if (nodeTest.isNameTest()) {
        final PrefixedName prefixedName = nodeTest.getQName();
        assert prefixedName != null;
        final String p = prefixedName.getPrefix();

        int endIndex = prefixedName.getLocalName().indexOf(CompletionLists.INTELLIJ_IDEA_RULEZ);
        if (endIndex != -1) {
          prefix = prefixedName.getLocalName().substring(0, endIndex);
        } else if (p != null) {
          endIndex = p.indexOf(CompletionLists.INTELLIJ_IDEA_RULEZ);
          if (endIndex != -1) {
            prefix = p.substring(0, endIndex);
          }
        }
      }
    }

    return prefix;
  }

  private static class MyLookup extends AbstractLookup {
    public MyLookup(String name) {
      super(name, name);
    }
  }
}
