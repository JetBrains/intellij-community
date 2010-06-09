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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.XmlRefCountHolder;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlUnusedNamespaceInspection extends XmlSuppressableInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {

    return new XmlElementVisitor() {

      @Override
      public void visitXmlAttribute(XmlAttribute attribute) {

        XmlRefCountHolder refCountHolder = XmlRefCountHolder.getRefCountHolder(attribute);
        if (refCountHolder == null) return;
        if (attribute.isNamespaceDeclaration()) {
          String namespace = attribute.getValue();
          if (namespace != null && !refCountHolder.isInUse(namespace)) {

            /*
            final ImplicitUsageProvider[] implicitUsageProviders = Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
            for (ImplicitUsageProvider provider : implicitUsageProviders) {
              if (provider.isImplicitUsage(attribute)) return;
            }
            */

            XmlAttributeValue value = attribute.getValueElement();
            assert value != null;
            holder.registerProblem(value, "Namespace declaration is not in use", ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                   new RemoveNamespaceDeclarationFix());
          }
        }
      }
    };
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return XmlBundle.message("xml.inspections.group.name");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Unused XML schema declaration";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "XmlUnusedNamespaceDeclaration";
  }

  private static class RemoveNamespaceDeclarationFix implements LocalQuickFix {
    @NotNull
    public String getName() {
      return "Remove namespace declaration";
    }

    @NotNull
    public String getFamilyName() {
      return XmlBundle.message("xml.inspections.group.name");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof XmlAttributeValue) {
        String namespace = ((XmlAttributeValue)element).getValue();
        PsiElement attribute = element.getParent();
        boolean def = !((XmlAttribute)attribute).getName().contains(":");
        XmlTag parent = (XmlTag)element.getParent().getParent();
        SmartPsiElementPointer<XmlTag> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(parent);

        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        Document document = documentManager.getDocument(element.getContainingFile());
        assert document != null;
        attribute.delete();
        if (def) {
          XmlAttribute locationAttr = parent.getAttribute(XmlUtil.NO_NAMESPACE_SCHEMA_LOCATION_ATT, XmlUtil.XML_SCHEMA_INSTANCE_URI);
          if (locationAttr != null) {
            locationAttr.delete();
          }
        }
        else {
          documentManager.doPostponedOperationsAndUnblockDocument(document);
          removeLocations(namespace, parent);
          documentManager.commitDocument(document);
        }
        XmlTag tag = pointer.getElement();
        assert tag != null;
        CodeStyleManager.getInstance(project).reformat(tag);
      }
    }

    private static void removeLocations(String namespace, XmlTag tag) {
      assert tag != null;
      XmlAttribute locationAttr = tag.getAttribute(XmlUtil.SCHEMA_LOCATION_ATT, XmlUtil.XML_SCHEMA_INSTANCE_URI);
      if (locationAttr != null) {
        String location = locationAttr.getValue();
        XmlAttributeValue value = locationAttr.getValueElement();
        assert value != null;
        PsiReference[] references = value.getReferences();
        for (int i = 0, referencesLength = references.length; i < referencesLength; i++) {
          PsiReference reference = references[i];
          if (namespace.equals(reference.getRangeInElement().shiftRight(-1).substring(location))) {
            if (i + 1 < referencesLength) {
              removeReferenceText(references[i + 1]);
            }
            removeReferenceText(reference);
            break;
          }
        }
      }
    }

    public static void removeReferenceText(PsiReference ref) {
      PsiElement element = ref.getElement();
      PsiFile file = element.getContainingFile();
      Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
      TextRange range = ref.getRangeInElement().shiftRight(element.getTextRange().getStartOffset());
      assert document != null;
      document.deleteString(range.getStartOffset(), range.getEndOffset());
    }
  }
}
