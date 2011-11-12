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
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URLReference;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.util.XmlRefCountHolder;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class XmlUnusedNamespaceInspection extends XmlSuppressableInspectionTool {

  private static final String NAMESPACE_LOCATION_IS_NEVER_USED = "Namespace location is never used";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {

    return new XmlElementVisitor() {

      @Override
      public void visitXmlAttribute(XmlAttribute attribute) {
        if (!attribute.isNamespaceDeclaration()) {
          checkUnusedLocations(attribute, holder);
          return;
        }
        XmlRefCountHolder refCountHolder = XmlRefCountHolder.getRefCountHolder(attribute);
        if (refCountHolder == null) return;

        String namespace = attribute.getValue();
        String declaredPrefix = getDeclaredPrefix(attribute);
        if (namespace != null && !refCountHolder.isInUse(declaredPrefix)) {

          ImplicitUsageProvider[] implicitUsageProviders = Extensions.getExtensions(ImplicitUsageProvider.EP_NAME);
          for (ImplicitUsageProvider provider : implicitUsageProviders) {
            if (provider.isImplicitUsage(attribute)) return;
          }

          XmlAttributeValue value = attribute.getValueElement();
          assert value != null;
          holder.registerProblem(attribute, "Namespace declaration is never used", ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                 new RemoveNamespaceDeclarationFix(declaredPrefix));

          XmlTag parent = attribute.getParent();
          if (declaredPrefix.length() == 0) {
            XmlAttribute location = getDefaultLocation(parent);
            if (location != null) {
              holder.registerProblem(location, NAMESPACE_LOCATION_IS_NEVER_USED, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                     new RemoveNamespaceDeclarationFix(declaredPrefix));
            }
          }
          else {
            for (PsiReference reference : getLocationReferences(namespace, parent)) {
              if (!XmlHighlightVisitor.hasBadResolve(reference, false))
              holder.registerProblemForReference(reference, ProblemHighlightType.LIKE_UNUSED_SYMBOL, NAMESPACE_LOCATION_IS_NEVER_USED,
                                                 new RemoveNamespaceDeclarationFix(declaredPrefix));
            }
          }
        }
      }
    };
  }

  private static void removeReferencesOrAttribute(PsiReference[] references) {
    if (references.length == 0) {
      return;
    }
    
    XmlAttributeValue element = (XmlAttributeValue)references[0].getElement();
    XmlAttribute attribute = (XmlAttribute)element.getParent();
    if (element.getReferences().length == references.length) { // all refs to be removed
      attribute.delete();
      return;
    }

    PsiFile file = element.getContainingFile();
    Project project = file.getProject();
    SmartPsiElementPointer<XmlAttribute> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(attribute);
    for (PsiReference reference : references) {
      RemoveNamespaceDeclarationFix.removeReferenceText(reference);
    }

    // trimming the result
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(file);
    assert document != null;
    documentManager.commitDocument(document);
    String trimmed = element.getValue().trim();
    XmlAttribute pointerElement = pointer.getElement();
    assert pointerElement != null;
    pointerElement.setValue(trimmed);
  }

  private static void checkUnusedLocations(XmlAttribute attribute, ProblemsHolder holder) {
    if (XmlUtil.XML_SCHEMA_INSTANCE_URI.equals(attribute.getNamespace())) {
      if (XmlUtil.NO_NAMESPACE_SCHEMA_LOCATION_ATT.equals(attribute.getLocalName())) {
        XmlRefCountHolder refCountHolder = XmlRefCountHolder.getRefCountHolder(attribute);
        if (refCountHolder == null || refCountHolder.isInUse("")) return;
        holder.registerProblem(attribute, NAMESPACE_LOCATION_IS_NEVER_USED, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                               new RemoveNamespaceLocationFix(""));
      }
      else if (XmlUtil.SCHEMA_LOCATION_ATT.equals(attribute.getLocalName())) {
        XmlAttributeValue value = attribute.getValueElement();
        if (value == null) return;
        PsiReference[] references = value.getReferences();
        for (int i = 0, referencesLength = references.length; i < referencesLength; i++) {
          PsiReference reference = references[i];
          if (reference instanceof URLReference) {
            String ns = getNamespaceFromReference(reference);
            if (ArrayUtil.indexOf(attribute.getParent().knownNamespaces(), ns) == -1) {
              if (!XmlHighlightVisitor.hasBadResolve(reference, false)) {
                holder.registerProblemForReference(reference, ProblemHighlightType.LIKE_UNUSED_SYMBOL, NAMESPACE_LOCATION_IS_NEVER_USED,
                                                   new RemoveNamespaceLocationFix(ns));
              }
              for (int j = i + 1; j < referencesLength; j++) {
                PsiReference nextRef = references[j];
                if (nextRef instanceof URLReference) break;
                if (!XmlHighlightVisitor.hasBadResolve(nextRef, false)) {
                  holder.registerProblemForReference(nextRef, ProblemHighlightType.LIKE_UNUSED_SYMBOL, NAMESPACE_LOCATION_IS_NEVER_USED,
                                                     new RemoveNamespaceLocationFix(ns));
                }
              }
            }
          }
        }
      }
    }
  }

  private static String getDeclaredPrefix(XmlAttribute attribute) {
    return attribute.getName().contains(":") ? attribute.getLocalName() : "";
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
    XmlAttributeValue value = locationAttr.getValueElement();
    assert value != null;
    return getLocationReferences(namespace, value);
  }

  private static PsiReference[] getLocationReferences(String namespace, XmlAttributeValue value) {
    PsiReference[] references = value.getReferences();
    for (int i = 0, referencesLength = references.length; i < referencesLength; i++) {
      PsiReference reference = references[i];
      if (namespace.equals(getNamespaceFromReference(reference))) {
        if (i + 1 < referencesLength) {
          return new PsiReference[] { references[i + 1], reference };
        }
        else {
          return new PsiReference[] { reference };
        }
      }
    }
    return PsiReference.EMPTY_ARRAY;
  }

  private static String getNamespaceFromReference(PsiReference reference) {
    return reference.getRangeInElement().substring(reference.getElement().getText());
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

  public static class RemoveNamespaceDeclarationFix implements LocalQuickFix {

    public static final String NAME = "Remove unused namespace declaration";

    protected final String myPrefix;

    private RemoveNamespaceDeclarationFix(@Nullable String prefix) {
      myPrefix = prefix;
    }

    @NotNull
    public String getName() {
      return NAME;
    }

    @NotNull
    public String getFamilyName() {
      return XmlBundle.message("xml.inspections.group.name");
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      doFix(project, descriptor, true);
    }

    @Nullable
    public SmartPsiElementPointer<XmlTag> doFix(Project project, ProblemDescriptor descriptor, boolean reformat) {
      PsiElement element = descriptor.getPsiElement();
      if (element instanceof XmlAttributeValue) {
        element = element.getParent();
      }
      else if (!(element instanceof XmlAttribute)) {
        return null;
      }
      XmlAttribute attribute = (XmlAttribute)element;
      XmlTag parent = attribute.getParent();

      if (!CodeInsightUtilBase.prepareFileForWrite(parent.getContainingFile())) return null;

      SmartPsiElementPointer<XmlTag> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(parent);

      doRemove(project, attribute, parent);

      if (reformat) {
        reformatStartTag(project, pointer);
      }
      return pointer;
    }

    public static void reformatStartTag(Project project, SmartPsiElementPointer<XmlTag> pointer) {
      PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
      PsiFile file = pointer.getContainingFile();
      assert file != null;
      Document document = manager.getDocument(file);
      assert document != null;
      manager.commitDocument(document);
      XmlTag tag = pointer.getElement();
      assert tag != null;
      XmlUtil.reformatTagStart(tag);
    }

    protected void doRemove(Project project, XmlAttribute attribute, XmlTag parent) {
      if (!attribute.isNamespaceDeclaration()) {
        SchemaPrefix schemaPrefix = XmlExtension.DEFAULT_EXTENSION.getPrefixDeclaration(parent, myPrefix);
        if (schemaPrefix != null) {
          attribute = schemaPrefix.getDeclaration();
        }
      }
      String namespace = attribute.getValue();
      String prefix = getDeclaredPrefix(attribute);

      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      Document document = documentManager.getDocument(attribute.getContainingFile());
      assert document != null;
      attribute.delete();
      if (prefix.length() == 0) {
        XmlAttribute locationAttr = getDefaultLocation(parent);
        if (locationAttr != null) {
          locationAttr.delete();
        }
      }
      else {
        documentManager.doPostponedOperationsAndUnblockDocument(document);
        PsiReference[] references = getLocationReferences(namespace, parent);
        removeReferencesOrAttribute(references);
        documentManager.commitDocument(document);
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

    @Override
    public boolean equals(Object obj) {
      return obj instanceof RemoveNamespaceDeclarationFix && Comparing.equal(myPrefix, ((RemoveNamespaceDeclarationFix)obj).myPrefix);
    }

    @Override
    public int hashCode() {
      return myPrefix == null ? 0 : myPrefix.hashCode();
    }
  }

  public static class RemoveNamespaceLocationFix extends RemoveNamespaceDeclarationFix {

    public static final String NAME = "Remove unused namespace location";

    private RemoveNamespaceLocationFix(String namespace) {
      super(namespace);
    }

    @NotNull
    @Override
    public String getName() {
      return NAME;
    }

    @Override
    protected void doRemove(Project project, XmlAttribute attribute, XmlTag parent) {
      if (myPrefix.length() == 0) {
        attribute.delete();
      }
      else {
        XmlAttributeValue value = attribute.getValueElement();
        if (value == null) {
          return;
        }
        PsiReference[] references = getLocationReferences(myPrefix, value);
        removeReferencesOrAttribute(references);
      }
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }
  }
}
