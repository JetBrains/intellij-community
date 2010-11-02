/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.XmlContentDFA;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.actions.ValidateXmlActionHandler;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.xs.SubstitutionGroupHandler;
import org.apache.xerces.impl.xs.XSComplexTypeDecl;
import org.apache.xerces.impl.xs.XSElementDecl;
import org.apache.xerces.impl.xs.XSGrammarBucket;
import org.apache.xerces.impl.xs.models.CMBuilder;
import org.apache.xerces.impl.xs.models.CMNodeFactory;
import org.apache.xerces.impl.xs.models.XSCMValidator;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.grammars.Grammar;
import org.apache.xerces.xni.grammars.XMLGrammarDescription;
import org.apache.xerces.xni.grammars.XMLGrammarPool;
import org.apache.xerces.xni.grammars.XSGrammar;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSTypeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class XmlSmartCompletionProvider {

  public void complete(CompletionParameters parameters, final CompletionResultSet result, PsiElement element) {
    if (!XmlCompletionContributor.isXmlNameCompletion(parameters)) {
      return;
    }
    result.stopHere();
    if (!(element.getParent() instanceof XmlTag)) {
      return;
    }
    final XmlTag tag = (XmlTag)element.getParent();
    final XmlTag parentTag = tag.getParentTag();
    final PsiFile file = tag.getContainingFile().getOriginalFile();
    if (!(file instanceof XmlFile)) return;
    XSModel xsModel = ApplicationManager.getApplication().runReadAction(new NullableComputable<XSModel>() {
      @Override
      public XSModel compute() {
        return getXSModel((XmlFile)file);
      }
    });
    if (xsModel != null) {
      processXsModel(result, tag, parentTag, xsModel);
    }
    else {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          processDtd(result, tag, parentTag);
        }
      });
    }
  }

  private static void processDtd(CompletionResultSet result, XmlTag tag, XmlTag parentTag) {
    XmlElementDescriptor descriptor = parentTag.getDescriptor();
    if (descriptor == null) {
      return;
    }
    XmlElementsGroup topGroup = descriptor.getTopGroup();
    if (topGroup == null) {
      return;
    }
    XmlContentDFA nfa = new XmlContentDFA(topGroup);
    for (XmlTag subTag : parentTag.getSubTags()) {
      if (subTag == tag) {
        break;
      }
      XmlElementDescriptor childDescriptor = subTag.getDescriptor();
      if (childDescriptor != null) {
        nfa.transition(childDescriptor);
      }
    }
    List<XmlElementDescriptor> elements = new ArrayList<XmlElementDescriptor>();
    nfa.getPossibleElements(elements);
    for (XmlElementDescriptor elementDescriptor : elements) {
      addElementToResult(elementDescriptor, result);
    }
  }

  private static void processXsModel(final CompletionResultSet result, XmlTag tag, final XmlTag parentTag, XSModel xsModel) {
    XSElementDeclaration decl = getElementDeclaration(parentTag, xsModel);
    if (decl == null) {
      return;
    }
    XSComplexTypeDecl definition = (XSComplexTypeDecl)decl.getTypeDefinition();
    XSCMValidator model = definition.getContentModel(new CMBuilder(new CMNodeFactory()));
    SubstitutionGroupHandler handler = new SubstitutionGroupHandler(new XSGrammarBucket());
    int[] state = model.startContentModel();
    for (XmlTag xmlTag : parentTag.getSubTags()) {
      if (xmlTag == tag) {
        break;
      }
      model.oneTransition(createQName(xmlTag), state, handler);
    }

    final List vector = model.whatCanGoHere(state);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        XmlElementDescriptor parentTagDescriptor = parentTag.getDescriptor();
        assert parentTagDescriptor != null;
        XmlElementDescriptor[] descriptors = parentTagDescriptor.getElementsDescriptors(parentTag);
        for (Object o : vector) {
          if (o instanceof XSElementDecl) {
            final XSElementDecl elementDecl = (XSElementDecl)o;
            XmlElementDescriptor descriptor = ContainerUtil.find(descriptors, new Condition<XmlElementDescriptor>() {
              @Override
              public boolean value(XmlElementDescriptor elementDescriptor) {
                return elementDecl.getName().equals(elementDescriptor.getName());
              }
            });
            if (descriptor != null) {
              addElementToResult(descriptor, result);
            }
          }
        }
      }
    });
  }

  private static void addElementToResult(@NotNull XmlElementDescriptor descriptor, CompletionResultSet result) {
    LookupElementBuilder builder = createLookupElement(descriptor);
    result.addElement(builder.setInsertHandler(new InsertHandler<LookupElement>() {
      @Override
      public void handleInsert(InsertionContext context, LookupElement item) {
        XmlTagInsertHandler.INSTANCE.handleInsert(context, item);
      }
    }));
  }

  public static LookupElementBuilder createLookupElement(@NotNull XmlElementDescriptor descriptor) {
    LookupElementBuilder builder = LookupElementBuilder.create(descriptor.getName());
    if (descriptor instanceof XmlElementDescriptorImpl) {
      builder = builder.setTypeText(((XmlElementDescriptorImpl)descriptor).getNamespace(), true);
    }
    return builder;
  }

  @Nullable
  private static XSElementDeclaration getElementDeclaration(XmlTag tag, XSModel xsModel) {

    List<XmlTag> ancestors = new ArrayList<XmlTag>();
    for (XmlTag t = tag; t != null; t = t.getParentTag()) {
      ancestors.add(t);
    }
    Collections.reverse(ancestors);
    XSElementDeclaration declaration = null;
    SubstitutionGroupHandler fSubGroupHandler = new SubstitutionGroupHandler(new XSGrammarBucket());
    CMBuilder cmBuilder = new CMBuilder(new CMNodeFactory());
    for (XmlTag ancestor : ancestors) {
      if (declaration == null) {
        declaration = xsModel.getElementDeclaration(ancestor.getLocalName(), ancestor.getNamespace());
        if (declaration == null) return null;
        else continue;
      }
      XSTypeDefinition typeDefinition = declaration.getTypeDefinition();
      if (!(typeDefinition instanceof XSComplexTypeDecl)) {
        return null;
      }

      XSCMValidator model = ((XSComplexTypeDecl)typeDefinition).getContentModel(cmBuilder);
      int[] ints = model.startContentModel();
      for (XmlTag subTag : ancestor.getParentTag().getSubTags()) {
        QName qName = createQName(subTag);
        Object o = model.oneTransition(qName, ints, fSubGroupHandler);
        if (subTag == ancestor) {
          if (o instanceof XSElementDecl) {
            declaration = (XSElementDecl)o;
            break;
          }
          else return null;
        }
      }
    }
    return declaration;
  }

  private static QName createQName(XmlTag tag) {
    String namespace = tag.getNamespace();
    return new QName(tag.getNamespacePrefix().intern(),
                     tag.getLocalName().intern(),
                     tag.getName().intern(),
                     namespace.length() == 0 ? null : namespace.intern());
  }


  @Nullable
  private XSModel getXSModel(XmlFile file) {

    ValidateXmlActionHandler handler = new ValidateXmlActionHandler(false) {
      @Override
      protected SAXParser createParser() throws SAXException, ParserConfigurationException {
        SAXParser parser = super.createParser();
        parser.getXMLReader().setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.CONTINUE_AFTER_FATAL_ERROR_FEATURE, true);
        return parser;
      }
    };
    handler.setErrorReporter(handler.new TestErrorReporter());
    handler.doValidate(file);
    XMLGrammarPool grammarPool = ValidateXmlActionHandler.getGrammarPool(file);
    if (grammarPool == null) {
      return null;
    }
    Grammar[] grammars = grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA);

    return grammars.length == 0 ? null : ((XSGrammar)grammars[0]).toXSModel(ContainerUtil.map(grammars, new Function<Grammar, XSGrammar>() {
      @Override
      public XSGrammar fun(Grammar grammar) {
        return (XSGrammar)grammar;
      }
    }, new XSGrammar[0]));
  }
}
