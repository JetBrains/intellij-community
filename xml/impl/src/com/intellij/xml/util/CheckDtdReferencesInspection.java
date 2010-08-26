/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.xml.util;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInspection.*;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * @author Maxim Mossienko
 */
public class CheckDtdReferencesInspection extends XmlSuppressableInspectionTool {
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override public void visitXmlElement(final XmlElement element) {
        if (element instanceof XmlElementContentSpec ||
            element instanceof XmlEntityRef
           ) {
          doCheckRefs(element, holder);
        }
      }
    };
  }

  private static void doCheckRefs(final XmlElement element, final ProblemsHolder holder) {
    for(PsiReference ref:element.getReferences()) {
      ProgressManager.checkCanceled();
      if (XmlHighlightVisitor.hasBadResolve(ref, true)) {
        if (ref.getElement() instanceof XmlElementContentSpec) {
          final String image = ref.getCanonicalText();
          if (image.equals("-") || image.equals("O")) continue;
        }
        holder.registerProblem(ref, XmlHighlightVisitor.getErrorDescription(ref), ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @NotNull
  public String getGroupDisplayName() {
    return XmlInspectionGroupNames.XML_INSPECTIONS;
  }

  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("xml.inspections.check.dtd.references");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "CheckDtdRefs";
  }

  public static class AddDtdDeclarationFix implements LocalQuickFix {
    private final String myMessageKey;
    private final String myElementDeclarationName;
    private final PsiReference myReference;

    public AddDtdDeclarationFix(
      @PropertyKey(resourceBundle=XmlBundle.PATH_TO_BUNDLE) String messageKey,
      @NotNull String elementDeclarationName,
      @NotNull PsiReference reference)
    {
      myMessageKey = messageKey;
      myElementDeclarationName = elementDeclarationName;
      myReference = reference;
    }

    @NotNull
    public String getName() {
      return XmlBundle.message(myMessageKey, myReference.getCanonicalText());
    }

    @NotNull
    public String getFamilyName() {
      return getName();
    }

    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiFile containingFile = element.getContainingFile();
      if (!CodeInsightUtilBase.prepareFileForWrite(containingFile)) return;

      @NonNls String prefixToInsert = "";
      @NonNls String suffixToInsert = "";

      final int UNDEFINED_OFFSET = -1;
      int anchorOffset = UNDEFINED_OFFSET;
      PsiElement anchor = PsiTreeUtil.getParentOfType(element, XmlElementDecl.class, XmlAttlistDecl.class, XmlEntityDecl.class, XmlConditionalSection.class);
      if (anchor != null) anchorOffset = anchor.getTextRange().getStartOffset();

      if (anchorOffset == UNDEFINED_OFFSET && containingFile.getLanguage() == XMLLanguage.INSTANCE) {
        XmlFile file = (XmlFile)containingFile;
        final XmlProlog prolog = file.getDocument().getProlog();
        assert prolog != null;
        
        final XmlDoctype doctype = prolog.getDoctype();
        final XmlMarkupDecl markupDecl;

        if (doctype != null) {
          markupDecl = doctype.getMarkupDecl();
        } else {
          markupDecl = null;
        }

        if (doctype == null) {
          final XmlTag rootTag = file.getDocument().getRootTag();
          prefixToInsert = "<!DOCTYPE " + ((rootTag != null)? rootTag.getName():"null");
          suffixToInsert = ">\n";
        }
        if (markupDecl == null) {
          prefixToInsert +=  " [\n";
          suffixToInsert =  "]" + suffixToInsert;

          if (doctype != null) {
            anchorOffset = doctype.getTextRange().getEndOffset() - 1; // just before last '>'
          } else {
            anchorOffset = prolog.getTextRange().getEndOffset();
          }
        }

      }

      if(anchorOffset == UNDEFINED_OFFSET) anchorOffset = element.getTextRange().getStartOffset();

      OpenFileDescriptor openDescriptor = new OpenFileDescriptor(project, containingFile.getVirtualFile(), anchorOffset);
      final Editor editor = FileEditorManager.getInstance(project).openTextEditor(openDescriptor, true);
      final TemplateManager templateManager = TemplateManager.getInstance(project);
      final Template t = templateManager.createTemplate("","");

      if (prefixToInsert.length() > 0) t.addTextSegment(prefixToInsert);
      t.addTextSegment("<!" + myElementDeclarationName + " " + myReference.getCanonicalText()+ " ");
      t.addEndVariable();
      t.addTextSegment(">\n");
      if (suffixToInsert.length() > 0) t.addTextSegment(suffixToInsert);
      templateManager.startTemplate(editor, t);
    }
  }
}
