package com.intellij.xml.util;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlDuplicatedIdInspection extends LocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttributeValue(final XmlAttributeValue value) {
        final PsiFile file = value.getContainingFile();
        if (file instanceof XmlFile) {
          final XmlRefCountHolder refHolder = XmlRefCountHolder.getInstance((XmlFile)file);
          if (refHolder == null) return;

          final PsiElement parent = value.getParent();
          if (!(parent instanceof XmlAttribute)) return;
          final XmlTag tag = ((XmlAttribute)parent).getParent();
          if (tag == null) return;

          if (refHolder.isValidatable(tag.getParent()) && refHolder.isDuplicateIdAttributeValue(value)) {
            holder.registerProblem(value, XmlErrorMessages.message("duplicate.id.reference"), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
          }

          String idRef = XmlHighlightVisitor.getUnquotedValue(value, tag);

          if (tag instanceof HtmlTag) {
            idRef = idRef.toLowerCase();
          }

          if (XmlUtil.isSimpleXmlAttributeValue(idRef, value) && refHolder.isIdReferenceValue(value)) {
            boolean hasIdDeclaration = refHolder.hasIdDeclaration(idRef);
            if (!hasIdDeclaration && tag instanceof HtmlTag) {
              hasIdDeclaration = refHolder.hasIdDeclaration(StringUtil.stripQuotesAroundValue(value.getText()));
            }

            if (!hasIdDeclaration) {
              final FileViewProvider viewProvider = tag.getContainingFile().getViewProvider();
              if (viewProvider instanceof TemplateLanguageFileViewProvider) {
                holder.registerProblem(value, XmlErrorMessages.message("invalid.id.reference"), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
                                       new XmlDeclareIdInCommentAction(idRef));

              }
              else {
                holder.registerProblem(value, XmlErrorMessages.message("invalid.id.reference"), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
              }
            }
          }
        }
      }
    };
  }

  public boolean runForWholeFile() {
    return false;
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
}
