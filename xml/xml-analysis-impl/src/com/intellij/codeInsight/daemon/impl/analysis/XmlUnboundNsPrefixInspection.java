// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.xml.SchemaPrefixReference;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlUnboundNsPrefixInspection extends XmlSuppressableInspectionTool {

  @NonNls private static final String XML = "xml";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new XmlElementVisitor() {

      private Boolean isXml;

      private boolean isXmlFile(XmlElement element) {
        if (isXml == null) {
          final PsiFile file = element.getContainingFile();
          isXml = file instanceof XmlFile && !InjectedLanguageManager.getInstance(element.getProject()).isInjectedFragment(file);
        }
        return isXml.booleanValue();
      }

      @Override
      public void visitXmlToken(final @NotNull XmlToken token) {
        if (isXmlFile(token) && token.getTokenType() == XmlTokenType.XML_NAME) {
          PsiElement element = token.getPrevSibling();
          while(element instanceof PsiWhiteSpace) element = element.getPrevSibling();

          if (element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_START_TAG_START) {
            PsiElement parent = element.getParent();

            if (parent instanceof XmlTag tag && !(token.getNextSibling() instanceof OuterLanguageElement)) {
              checkUnboundNamespacePrefix(tag, tag, tag.getNamespacePrefix(), token, holder, isOnTheFly);
            }
          }
        }
      }

      @Override
      public void visitXmlAttribute(final @NotNull XmlAttribute attribute) {
        if (!isXmlFile(attribute)) {
          return;
        }
        final String namespace = attribute.getNamespace();
        if (attribute.isNamespaceDeclaration() || XmlUtil.XML_SCHEMA_INSTANCE_URI.equals(namespace)) {
          return;
        }

        XmlTag tag = attribute.getParent();
        if (tag == null) return;
        XmlElementDescriptor elementDescriptor = tag.getDescriptor();
        if (elementDescriptor == null ||
            elementDescriptor instanceof AnyXmlElementDescriptor) {
          return;
        }


        final String name = attribute.getName();

        checkUnboundNamespacePrefix(attribute, tag, XmlUtil.findPrefixByQualifiedName(name), null, holder, isOnTheFly);
      }

      @Override
      public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
        PsiReference[] references = value.getReferences();
        for (PsiReference reference : references) {
          if (reference instanceof SchemaPrefixReference) {
            if (!XML.equals(((SchemaPrefixReference)reference).getNamespacePrefix()) && reference.resolve() == null) {
              holder.registerProblem(reference, XmlAnalysisBundle.message("xml.inspections.unbound.namespace",
                                                                          ((SchemaPrefixReference)reference).getNamespacePrefix()),
                                     ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
            }
          }
        }
      }
    };
  }

  private void checkUnboundNamespacePrefix(final XmlElement element, final XmlTag context, String namespacePrefix, final XmlToken token,
                                           final ProblemsHolder holder, boolean isOnTheFly) {

    if (namespacePrefix.isEmpty() && (!(element instanceof XmlTag) || !(element.getParent() instanceof XmlDocument))
        || XML.equals(namespacePrefix)) {
      return;
    }

    final String namespaceByPrefix = context.getNamespaceByPrefix(namespacePrefix);
    if (!namespaceByPrefix.isEmpty()) {
      return;
    }
    PsiFile psiFile = context.getContainingFile();
    if (!(psiFile instanceof XmlFile containingFile)) return;
    if (!HighlightingLevelManager.getInstance(containingFile.getProject()).shouldInspect(containingFile)) return;

    final XmlExtension extension = XmlExtension.getExtension(containingFile);
    if (extension.getPrefixDeclaration(context, namespacePrefix) != null) {
      return;
    }

    final String localizedMessage = isOnTheFly ? XmlAnalysisBundle.message("xml.inspections.unbound.namespace", namespacePrefix) : XmlAnalysisBundle
      .message("xml.inspections.unbound.namespace.no.param");

    if (namespacePrefix.isEmpty()) {
      final XmlTag tag = (XmlTag)element;
      if (!XmlUtil.JSP_URI.equals(tag.getNamespace()) && isOnTheFly) {
        LocalQuickFix fix = XmlQuickFixFactory.getInstance().createNSDeclarationIntentionFix(context, namespacePrefix, token);
        reportTagProblem(tag, localizedMessage, null, ProblemHighlightType.INFORMATION, fix, holder);
      }
      return;
    }

    final int prefixLength = namespacePrefix.length();
    final TextRange range = new TextRange(0, prefixLength);
    final HighlightInfoType infoType = extension.getHighlightInfoType(containingFile);
    final ProblemHighlightType highlightType =
      infoType == HighlightInfoType.ERROR ? ProblemHighlightType.ERROR : ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;

    if (isSuppressedFor(element)) return;
    if (element instanceof XmlTag) {
      LocalQuickFix fix =
        isOnTheFly ? XmlQuickFixFactory.getInstance().createNSDeclarationIntentionFix(context, namespacePrefix, token) : null;
      reportTagProblem(element, localizedMessage, range, highlightType, fix, holder);
    }
    else if (element instanceof XmlAttribute attribute) {
      LocalQuickFix fix =
        isOnTheFly ? XmlQuickFixFactory.getInstance().createNSDeclarationIntentionFix(element, namespacePrefix, token) : null;
      holder.registerProblem(attribute.getNameElement(), localizedMessage, highlightType, range, fix);
    }
    else {
      holder.registerProblem(element, localizedMessage, highlightType, range);
    }
  }

  private static void reportTagProblem(final XmlElement element, final @InspectionMessage String localizedMessage,
                                       final TextRange range, final ProblemHighlightType highlightType,
                                       final LocalQuickFix fix,
                                       final ProblemsHolder holder) {

    XmlToken nameToken = XmlTagUtil.getStartTagNameElement((XmlTag)element);
    if (nameToken != null) {
      holder.registerProblem(nameToken, localizedMessage, highlightType, range, fix);
    }
    nameToken = XmlTagUtil.getEndTagNameElement((XmlTag)element);
    if (nameToken != null) {
      holder.registerProblem(nameToken, localizedMessage, highlightType, range, fix);
    }
  }


  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "XmlUnboundNsPrefix";
  }
}

