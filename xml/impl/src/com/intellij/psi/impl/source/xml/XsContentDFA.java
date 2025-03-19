// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.actions.validate.ErrorReporter;
import com.intellij.xml.actions.validate.ValidateXmlActionHandler;
import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.xs.*;
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
import org.xml.sax.SAXParseException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
final class XsContentDFA extends XmlContentDFA {
  private final XSCMValidator myContentModel;
  private final SubstitutionGroupHandler myHandler;
  private final int[] myState;
  private final XmlElementDescriptor[] myElementDescriptors;

  public static @Nullable XmlContentDFA createContentDFA(@NotNull XmlTag parentTag) {
    final PsiFile file = parentTag.getContainingFile().getOriginalFile();
    if (!(file instanceof XmlFile)) return null;
    XSModel xsModel = ReadAction.compute(() -> getXSModel((XmlFile)file));
    if (xsModel == null) {
      return null;
    }

    XSElementDeclaration decl = getElementDeclaration(parentTag, xsModel);
    if (decl == null) {
      return null;
    }
    return new XsContentDFA(decl, parentTag);
  }

  XsContentDFA(@NotNull XSElementDeclaration decl, final XmlTag parentTag) {
    XSComplexTypeDecl definition = (XSComplexTypeDecl)decl.getTypeDefinition();
    myContentModel = definition.getContentModel(new CMBuilder(new CMNodeFactory()));
    myHandler = new SubstitutionGroupHandler(new MyXSElementDeclHelper());
    myState = myContentModel.startContentModel();
    myElementDescriptors = ReadAction.compute(() -> {
      XmlElementDescriptor parentTagDescriptor = parentTag.getDescriptor();
      assert parentTagDescriptor != null;
      return parentTagDescriptor.getElementsDescriptors(parentTag);
    });
  }

  @Override
  public List<XmlElementDescriptor> getPossibleElements() {
    final List vector = myContentModel.whatCanGoHere(myState);
    ArrayList<XmlElementDescriptor> list = new ArrayList<>();
    for (Object o : vector) {
      if (o instanceof XSElementDecl elementDecl) {
        XmlElementDescriptor descriptor = ContainerUtil.find(myElementDescriptors,
                                                             elementDescriptor -> elementDecl.getName().equals(elementDescriptor.getName()));
        ContainerUtil.addIfNotNull(list, descriptor);
      }
    }
    return list;
  }

  @Override
  public void transition(XmlTag xmlTag) {
    myContentModel.oneTransition(createQName(xmlTag), myState, myHandler);
  }

  private static QName createQName(XmlTag tag) {
    //todo don't use intern to not pollute PermGen
    String namespace = tag.getNamespace();
    return new QName(tag.getNamespacePrefix().intern(),
                     tag.getLocalName().intern(),
                     tag.getName().intern(),
                     namespace.isEmpty() ? null : namespace.intern());
  }

  private static @Nullable XSElementDeclaration getElementDeclaration(XmlTag tag, XSModel xsModel) {

    List<XmlTag> ancestors = new ArrayList<>();
    for (XmlTag t = tag; t != null; t = t.getParentTag()) {
      ancestors.add(t);
    }
    Collections.reverse(ancestors);
    XSElementDeclaration declaration = null;
    SubstitutionGroupHandler fSubGroupHandler = new SubstitutionGroupHandler(new MyXSElementDeclHelper());
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

  private static @Nullable XSModel getXSModel(XmlFile file) {

    ValidateXmlActionHandler handler = new ValidateXmlActionHandler(false) {
      @Override
      protected SAXParser createParser() throws SAXException, ParserConfigurationException {
        SAXParser parser = super.createParser();
        if (parser != null) {
          parser.getXMLReader().setFeature(Constants.XERCES_FEATURE_PREFIX + Constants.CONTINUE_AFTER_FATAL_ERROR_FEATURE, true);
        }
        return parser;
      }
    };
    handler.setErrorReporter(new ErrorReporter(handler) {

      int count;
      @Override
      public void processError(SAXParseException ex, ValidateXmlActionHandler.ProblemType warning) throws SAXException {
        if (warning != ValidateXmlActionHandler.ProblemType.WARNING && count++ > 100) {
          throw new SAXException(ex);
        }
      }

      @Override
      public boolean isUniqueProblem(SAXParseException e) {
        return true;
      }
    });

    handler.doValidate(file);
    XMLGrammarPool grammarPool = ValidateXmlActionHandler.getGrammarPool(file);
    if (grammarPool == null) {
      return null;
    }
    Grammar[] grammars = grammarPool.retrieveInitialGrammarSet(XMLGrammarDescription.XML_SCHEMA);

    return grammars.length == 0 ? null : ((XSGrammar)grammars[0]).toXSModel(ContainerUtil.map(grammars, grammar -> (XSGrammar)grammar, new XSGrammar[0]));
  }

  private static class MyXSElementDeclHelper implements XSElementDeclHelper {
    private final XSGrammarBucket myBucket = new XSGrammarBucket();

    @Override
    public XSElementDecl getGlobalElementDecl(QName name) {
      SchemaGrammar grammar = myBucket.getGrammar(name.uri);
      return grammar == null ? null : grammar.getGlobalElementDecl(name.localpart, name.prefix);
    }
  }
}
