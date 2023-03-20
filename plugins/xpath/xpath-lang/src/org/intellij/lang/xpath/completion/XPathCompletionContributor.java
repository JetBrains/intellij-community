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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.patterns.PatternCondition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.psi.*;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;
import java.util.Collection;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class XPathCompletionContributor extends CompletionContributor {

  public XPathCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().withParent(XPathNodeTest.class), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final XPathNodeTest nodeTest = (XPathNodeTest)parameters.getPosition().getParent();
        addResult(result, CompletionLists.getNodeTestCompletions(nodeTest), parameters);
      }
    });
    extend(CompletionType.BASIC, psiElement().withParent(psiElement(XPathNodeTest.class).with(prefix())), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final XPathNodeTest nodeTest = (XPathNodeTest)parameters.getPosition().getParent();
        addResult(result, CompletionLists.getFunctionCompletions(nodeTest), parameters);
      }
    });

    extend(CompletionType.BASIC, psiElement().withParent(XPathAxisSpecifier.class), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        addResult(result, CompletionLists.getAxisCompletions(), parameters);
      }
    });

    extend(CompletionType.BASIC, psiElement().withParent(XPathFunctionCall.class), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final XPathFunctionCall call = (XPathFunctionCall)parameters.getPosition().getParent();
        addResult(result, CompletionLists.getFunctionCompletions(call), parameters);
      }
    });
    extend(CompletionType.BASIC, psiElement().withParent(psiElement(XPathFunctionCall.class).without(prefix())),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               final XPathFunctionCall call = (XPathFunctionCall)parameters.getPosition().getParent();
               addResult(result, CompletionLists.getNodeTypeCompletions(call), parameters);
             }
           });

    extend(CompletionType.BASIC, psiElement().withParent(XPathVariableReference.class), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        addResult(result, CompletionLists.getVariableCompletions((XPathVariableReference)parameters.getPosition().getParent()), parameters);
      }
    });

    extend(CompletionType.BASIC, psiElement().withParent(psiElement(XPath2TypeElement.class).without(prefix())),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               final XPathElement parent = PsiTreeUtil.getParentOfType(parameters.getPosition(), XPathElement.class);
               assert parent != null;

               if (parent.getParent() instanceof XPath2TreatAs || parent.getParent() instanceof XPath2InstanceOf) {
                 addResult(result, CompletionLists.getNodeTypeCompletions(parent), parameters);
               }

               final NamespaceContext namespaceContext = parent.getXPathContext().getNamespaceContext();
               if (namespaceContext != null) {
                 final String prefixForURI =
                   namespaceContext.getPrefixForURI(XPath2Type.XMLSCHEMA_NS, parent.getXPathContext().getContextElement());
                 if (prefixForURI != null && prefixForURI.length() > 0) {
                   addResult(result, ContainerUtil.map(XPath2Type.SchemaType.listSchemaTypes(),
                                                       type -> new MyLookup(prefixForURI + ":" + type.getQName().getLocalPart())),
                             parameters);
                 }
               }
             }
           });
    extend(CompletionType.BASIC, psiElement().withParent(psiElement(XPath2TypeElement.class).with(prefix())), new CompletionProvider<>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    @NotNull ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        final XPath2TypeElement parent = PsiTreeUtil.getParentOfType(parameters.getPosition(), XPath2TypeElement.class);
        assert parent != null;

        final QName qName = parent.getXPathContext().getQName(parent);
        if (qName != null && qName.getNamespaceURI().equals(XPath2Type.XMLSCHEMA_NS)) {
          addResult(result,
                    ContainerUtil.map(XPath2Type.SchemaType.listSchemaTypes(), type -> new MyLookup(type.getQName().getLocalPart())),
                    parameters);
        }
      }
    });
  }

  private static PatternCondition<QNameElement> prefix() {
    return new PatternCondition<>("hasPrefix") {
      @Override
      public boolean accepts(@NotNull QNameElement qnameElement, ProcessingContext context) {
        final PrefixedName qname = qnameElement.getQName();
        return qname != null && qname.getPrefix() != null;
      }
    };
  }

  private static void addResult(CompletionResultSet result, Collection<LookupElement> collection, CompletionParameters parameters) {
    result.withPrefixMatcher(findPrefixStatic(parameters)).addAllElements(ContainerUtil.map(collection, e ->
      PrioritizedLookupElement.withPriority(e, e instanceof VariableLookup ? 2 : 1)));
  }

  @NotNull
  private static String findPrefixStatic(CompletionParameters parameters) {
    String prefix = CompletionUtil.findReferencePrefix(parameters);
    if (prefix == null) {
      prefix = CompletionUtil.findJavaIdentifierPrefix(parameters);
    }

    PsiElement element = parameters.getPosition();
    if (element.getParent() instanceof XPathVariableReference) {
      prefix = "$" + prefix;
    }

    if (element.getParent() instanceof XPathNodeTest nodeTest) {
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
    MyLookup(String name) {
      super(name, name);
    }
  }
}
