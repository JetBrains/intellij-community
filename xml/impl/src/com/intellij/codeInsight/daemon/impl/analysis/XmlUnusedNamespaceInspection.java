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
import org.jetbrains.annotations.Nullable;

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
          boolean def = isDefaultNamespace(attribute);
          String declaredPrefix = def ? "" : attribute.getLocalName();
          if (namespace != null && !refCountHolder.isInUse(declaredPrefix)) {

            XmlAttributeValue value = attribute.getValueElement();
            assert value != null;
            holder.registerProblem(attribute, "Namespace declaration is never used", ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                   new RemoveNamespaceDeclarationFix(declaredPrefix));

            String message = "Namespace location is never used";
            XmlTag parent = attribute.getParent();
            if (def) {
              XmlAttribute location = getDefaultLocation(parent);
              if (location != null) {
                holder.registerProblem(location, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL, new RemoveNamespaceDeclarationFix(declaredPrefix));
              }
            }
            else {
              for (PsiReference reference : getLocationReferences(namespace, parent)) {
                holder.registerProblem(reference, ProblemHighlightType.LIKE_UNUSED_SYMBOL, message, new RemoveNamespaceDeclarationFix(declaredPrefix));
              }
            }
          }
        }
      }
    };
  }

  public static boolean isDefaultNamespace(XmlAttribute attribute) {
    return !attribute.getName().contains(":");
  }

  @Nullable
  private static XmlAttribute getDefaultLocation(XmlTag parent) {
    return parent.getAttribute(XmlUtil.NO_NAMESPACE_SCHEMA_LOCATION_ATT, XmlUtil.XML_SCHEMA_INSTANCE_URI);
  }

  private static PsiReference[] getLocationReferences(String namespace, XmlTag tag) {
    XmlAttribute locationAttr = tag.getAttribute(XmlUtil.SCHEMA_LOCATION_ATT, XmlUtil.XML_SCHEMA_INSTANCE_URI);
    if (locationAttr == null) {
      return PsiReference.EMPTY_ARRAY;
    }
    String location = locationAttr.getValue();
    XmlAttributeValue value = locationAttr.getValueElement();
    assert value != null;
    PsiReference[] references = value.getReferences();
    for (int i = 0, referencesLength = references.length; i < referencesLength; i++) {
      PsiReference reference = references[i];
      if (namespace.equals(reference.getRangeInElement().shiftRight(-1).substring(location))) {
        if (i + 1 < referencesLength) {
          return new PsiReference[] { reference, references[i + 1] };
        }
        else {
          return new PsiReference[] { reference };
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
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

    private final String myPrefix;

    private RemoveNamespaceDeclarationFix(String prefix) {
      myPrefix = prefix;
    }

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
      if (!(element instanceof XmlAttribute)) {
        return;
      }
      doRemove(project, (XmlAttribute)element);
    }

    private static void doRemove(Project project, XmlAttribute attribute) {
      String namespace = attribute.getValue();
      boolean def = isDefaultNamespace(attribute);
      XmlTag parent = attribute.getParent();
      SmartPsiElementPointer<XmlTag> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(parent);

      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      Document document = documentManager.getDocument(attribute.getContainingFile());
      assert document != null;
      attribute.delete();
      if (def) {
        XmlAttribute locationAttr = getDefaultLocation(parent);
        if (locationAttr != null) {
          locationAttr.delete();
        }
      }
      else {
        documentManager.doPostponedOperationsAndUnblockDocument(document);
        PsiReference[] references = getLocationReferences(namespace, parent);
        for (PsiReference reference : references) {
          removeReferenceText(reference);
        }
        documentManager.commitDocument(document);
      }
      XmlTag tag = pointer.getElement();
      assert tag != null;
      CodeStyleManager.getInstance(project).reformat(tag);
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
