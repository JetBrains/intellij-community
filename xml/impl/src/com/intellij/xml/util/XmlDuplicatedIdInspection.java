package com.intellij.xml.util;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInsight.daemon.impl.analysis.XmlRefCountHolder;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class XmlDuplicatedIdInspection extends LocalInspectionTool {

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new XmlElementVisitor() {
      private XmlRefCountHolder myRefCountHolder;
      public void visitXmlAttributeValue(final XmlAttributeValue value) {
        if (myRefCountHolder == null) {
          final PsiFile psiFile = value.getContainingFile();
          if (psiFile instanceof XmlFile) {
            processFile((XmlFile)psiFile);
          }
        }
        if (myRefCountHolder != null) {
          checkIdRefAttrValue(value, myRefCountHolder, holder);
        }
      }

      private void processFile(final XmlFile file) {
        myRefCountHolder = XmlRefCountHolder.getInstance(file);
        for (Iterator<Map.Entry<PsiElement, Boolean>> iterator = myRefCountHolder.getPossiblyDuplicateElementsMap().entrySet().iterator(); iterator.hasNext();) {
          Map.Entry<PsiElement, Boolean> entry = iterator.next();
          final XmlAttributeValue value = (XmlAttributeValue)entry.getKey();

          if (value.isValid()) {
            processAttributeValue(value, entry.getValue().booleanValue(), myRefCountHolder, holder);
          }
          else {
            iterator.remove();
          }
        }
      }
    };
  }

  public boolean runForWholeFile() {
    return true;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public String getGroupDisplayName() {
    return XmlBundle.message("xml.inspections.group.name");
  }

  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("xml.inspections.duplicated.id");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "XmlDuplicatedId";
  }

  private static boolean isSoftContext(@NotNull final XmlAttribute attr) {
    final XmlAttributeDescriptor xmlAttributeDescriptor = attr.getDescriptor();
    assert xmlAttributeDescriptor != null;
    if (xmlAttributeDescriptor.hasIdType()) return false;
    final XmlAttributeValue element = attr.getValueElement();
    assert element != null;
    PsiReference reference = element.getReference();
    return reference != null && reference.isSoft();
  }

  private static void processAttributeValue(@NotNull XmlAttributeValue value,
                                            boolean soft, @NotNull final XmlRefCountHolder refCountHolder,
                                            ProblemsHolder holder) {
    final PsiElement parent = value.getParent();
    if (!(parent instanceof XmlAttribute)) return;
    XmlAttribute attribute = (XmlAttribute)parent;
    XmlTag tag = attribute.getParent();
    if (tag == null) return;

    final String unquotedValue = XmlHighlightVisitor.getUnquotedValue(value, tag);

    if (XmlUtil.isSimpleXmlAttributeValue(unquotedValue, value)) {
      final XmlAttribute attributeById = refCountHolder.getAttributeById(unquotedValue);

      if (attributeById == null ||
          !attributeById.isValid() ||
          attributeById == attribute ||
          soft ||
          isSoftContext(attributeById)
         ) {
        if (!soft || attributeById == null) refCountHolder.registerAttributeWithId(unquotedValue,attribute);
      } else {
        final XmlAttributeValue valueElement = attributeById.getValueElement();

        if (valueElement != null && XmlHighlightVisitor.getUnquotedValue(valueElement, tag).equals(unquotedValue)) {
          if (tag.getParent().getUserData(XmlHighlightVisitor.DO_NOT_VALIDATE_KEY) == null) {
            holder.registerProblem(value,
                                   XmlErrorMessages.message("duplicate.id.reference"),
                                   ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
            holder.registerProblem(valueElement,
                                   XmlErrorMessages.message("duplicate.id.reference"),
                                   ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
          }
        } else {
          // attributeById previously has that id so reregister new one
          refCountHolder.registerAttributeWithId(unquotedValue,attribute);
        }
      }
    }
  }

  private static void checkIdRefAttrValue(XmlAttributeValue value, @NotNull XmlRefCountHolder holder, ProblemsHolder problemsHolder) {
    if (!(value.getParent() instanceof XmlAttribute)) return;
    XmlAttribute attribute = (XmlAttribute)value.getParent();

    XmlTag tag = attribute.getParent();

    XmlElementDescriptor elementDescriptor = tag.getDescriptor();
    if (elementDescriptor == null) return;
    XmlAttributeDescriptor attributeDescriptor = elementDescriptor.getAttributeDescriptor(attribute);
    if (attributeDescriptor == null) return;

    if (attributeDescriptor.hasIdRefType() &&
        tag.getParent().getUserData(XmlHighlightVisitor.DO_NOT_VALIDATE_KEY) == null
       ) {
      String unquotedValue = XmlHighlightVisitor.getUnquotedValue(value, tag);
      if (XmlUtil.isSimpleXmlAttributeValue(unquotedValue, value)) {
        XmlAttribute xmlAttribute = holder.getAttributeById(unquotedValue);
        if (xmlAttribute == null && tag instanceof HtmlTag) {
          xmlAttribute = holder.getAttributeById(StringUtil.stripQuotesAroundValue(value.getText()));
        }

        if (xmlAttribute == null || !xmlAttribute.isValid()) {
          problemsHolder.registerProblem(value,
                                         XmlErrorMessages.message("invalid.id.reference"),
                                         ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        }
      }
    }
  }
}
