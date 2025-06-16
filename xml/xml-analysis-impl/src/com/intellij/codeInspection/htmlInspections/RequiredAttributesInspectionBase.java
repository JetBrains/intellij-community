// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightingAwareElementDescriptor;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.html.impl.providers.HtmlAttributeValueProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.containers.JBIterable;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import static com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor.isInjectedWithoutValidation;

public class RequiredAttributesInspectionBase extends HtmlLocalInspectionTool implements XmlEntitiesInspection {
  public static final @NonNls Key<InspectionProfileEntry> SHORT_NAME_KEY = Key.create(REQUIRED_ATTRIBUTES_SHORT_NAME);
  protected static final Logger LOG = Logger.getInstance(RequiredAttributesInspectionBase.class);
  public @NlsSafe String myAdditionalRequiredHtmlAttributes = "";

  private static String appendName(String toAppend, String text) {
    if (!toAppend.isEmpty()) {
      toAppend += "," + text;
    }
    else {
      toAppend = text;
    }
    return toAppend;
  }

  @Override
  public @NotNull @NonNls String getShortName() {
    return REQUIRED_ATTRIBUTES_SHORT_NAME;
  }

  @Override
  public String getAdditionalEntries() {
    return myAdditionalRequiredHtmlAttributes;
  }

  @Override
  public void addEntry(String text) {
    myAdditionalRequiredHtmlAttributes = appendName(getAdditionalEntries(), text);
  }

  public static @NotNull LocalQuickFix getIntentionAction(String name) {
    return new AddHtmlTagOrAttributeToCustomsIntention(SHORT_NAME_KEY, name, XmlAnalysisBundle.message(
      "html.quickfix.add.optional.html.attribute", name));
  }

  @Override
  protected void checkTag(@NotNull XmlTag tag, @NotNull ProblemsHolder holder, boolean isOnTheFly) {

    String name = tag.getName();
    XmlElementDescriptor elementDescriptor = XmlUtil.getDescriptorFromContext(tag);
    if (elementDescriptor instanceof AnyXmlElementDescriptor || elementDescriptor == null) {
      elementDescriptor = tag.getDescriptor();
    }

    if (elementDescriptor == null) return;
    if ((elementDescriptor instanceof XmlHighlightingAwareElementDescriptor) &&
        !((XmlHighlightingAwareElementDescriptor)elementDescriptor).shouldCheckRequiredAttributes()) {
      return;
    }

    XmlAttributeDescriptor[] attributeDescriptors = elementDescriptor.getAttributesDescriptors(tag);
    Set<String> requiredAttributes = null;

    for (XmlAttributeDescriptor attribute : attributeDescriptors) {
      if (attribute != null && attribute.isRequired()) {
        if (requiredAttributes == null) {
          requiredAttributes = new HashSet<>();
        }
        requiredAttributes.add(attribute.getName(tag));
      }
    }

    if (requiredAttributes != null) {
      for (String attrName : requiredAttributes) {
        if (!hasAttribute(tag, attrName) &&
            !XmlExtension.getExtension(tag.getContainingFile()).isRequiredAttributeImplicitlyPresent(tag, attrName)) {

          LocalQuickFix insertRequiredAttributeIntention =
            isOnTheFly ? XmlQuickFixFactory.getInstance().insertRequiredAttributeFix(tag, attrName) : null;
          reportOneTagProblem(
            tag,
            attrName,
            XmlAnalysisBundle.message("xml.inspections.element.doesnt.have.required.attribute", name, attrName),
            insertRequiredAttributeIntention,
            holder,
            getIntentionAction(attrName),
            isOnTheFly
          );
        }
      }
    }
  }

  private static boolean hasAttribute(XmlTag tag, String attrName) {
    if (JBIterable.from(HtmlAttributeValueProvider.EP_NAME.getExtensionList())
          .filterMap(it -> it.getCustomAttributeValue(tag, attrName)).first() != null) {
      return true;
    }
    final XmlAttribute attribute = tag.getAttribute(attrName);
    if (attribute == null) return false;
    if (attribute.getValueElement() != null) return true;
    if (!(tag instanceof HtmlTag)) return false;
    final XmlAttributeDescriptor descriptor = attribute.getDescriptor();
    return descriptor != null && HtmlUtil.isBooleanAttribute(descriptor, tag);
  }

  private void reportOneTagProblem(final XmlTag tag,
                                   final String name,
                                   @NotNull @InspectionMessage String localizedMessage,
                                   final LocalQuickFix basicIntention,
                                   ProblemsHolder holder,
                                   @NotNull LocalQuickFix addAttributeFix,
                                   boolean isOnTheFly) {
    boolean htmlTag = false;

    if (tag instanceof HtmlTag) {
      htmlTag = true;
      if (isAdditionallyDeclared(getAdditionalEntries(), name)) return;
    }

    LocalQuickFix[] fixes;
    ProblemHighlightType highlightType;
    if (htmlTag) {
      fixes = basicIntention == null ? new LocalQuickFix[]{addAttributeFix} : new LocalQuickFix[]{addAttributeFix, basicIntention};
      highlightType = isInjectedWithoutValidation(tag) ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    }
    else {
      fixes = basicIntention == null ? LocalQuickFix.EMPTY_ARRAY : new LocalQuickFix[]{basicIntention};
      highlightType = ProblemHighlightType.ERROR;
    }
    if (isOnTheFly || highlightType != ProblemHighlightType.INFORMATION) {
      addElementsForTag(tag, localizedMessage, highlightType, holder, isOnTheFly, fixes);
    }
  }

  private static void addElementsForTag(XmlTag tag,
                                        @InspectionMessage String message,
                                        ProblemHighlightType error,
                                        ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalQuickFix @NotNull ... fixes) {
    registerProblem(message, error, holder, XmlTagUtil.getStartTagNameElement(tag), fixes);
    if (isOnTheFly) {
      registerProblem(message, error, holder, XmlTagUtil.getEndTagNameElement(tag), fixes);
    }
  }

  private static void registerProblem(@InspectionMessage String message,
                                      ProblemHighlightType error,
                                      ProblemsHolder holder,
                                      XmlToken start,
                                      LocalQuickFix[] fixes) {
    if (start != null) {
      holder.registerProblem(start, message, error, fixes);
    }
  }

  private static boolean isAdditionallyDeclared(final String additional, String name) {
    name = StringUtil.toLowerCase(name);
    if (!additional.contains(name)) return false;

    StringTokenizer tokenizer = new StringTokenizer(additional, ", ");
    while (tokenizer.hasMoreTokens()) {
      if (name.equals(tokenizer.nextToken())) {
        return true;
      }
    }

    return false;
  }
}
