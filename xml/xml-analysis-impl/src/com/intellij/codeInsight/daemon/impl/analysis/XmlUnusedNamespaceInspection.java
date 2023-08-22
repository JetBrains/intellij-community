// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.URLReference;
import com.intellij.psi.impl.source.xml.SchemaPrefix;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.DefaultXmlExtension;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.util.XmlRefCountHolder;
import com.intellij.xml.util.XmlUtil;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public final class XmlUnusedNamespaceInspection extends XmlSuppressableInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlAttribute(@NotNull XmlAttribute attribute) {
        PsiFile file = holder.getFile();
        if (!(file instanceof XmlFile)) return;

        XmlRefCountHolder refCountHolder = XmlRefCountHolder.getRefCountHolder((XmlFile)file);
        if (refCountHolder == null) return;
        if (!attribute.isNamespaceDeclaration()) {
          checkUnusedLocations(attribute, holder, refCountHolder);
          return;
        }

        String namespace = attribute.getValue();
        String declaredPrefix = getDeclaredPrefix(attribute);
        if (namespace != null && !refCountHolder.isInUse(declaredPrefix)) {

          for (ImplicitUsageProvider provider : ImplicitUsageProvider.EP_NAME.getExtensionList()) {
            if (provider.isImplicitUsage(attribute)) return;
          }

          XmlAttributeValue value = attribute.getValueElement();
          assert value != null;
          holder.registerProblem(attribute, XmlAnalysisBundle.message("xml.inspections.unused.schema.declaration"), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                 new RemoveNamespaceDeclarationFix(declaredPrefix, false, !refCountHolder.isUsedNamespace(namespace)));

          XmlTag parent = attribute.getParent();
          if (declaredPrefix.isEmpty()) {
            XmlAttribute location = getDefaultLocation(parent);
            if (location != null) {
              holder.registerProblem(location, XmlAnalysisBundle.message("xml.inspections.unused.schema.location"), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                     new RemoveNamespaceDeclarationFix(declaredPrefix, true, true));
            }
          }
          else if (!refCountHolder.isUsedNamespace(namespace)) {
            for (PsiReference reference : getLocationReferences(namespace, parent)) {
              if (!XmlHighlightVisitor.hasBadResolve(reference, false))
                holder.registerProblemForReference(reference, ProblemHighlightType.LIKE_UNUSED_SYMBOL, XmlAnalysisBundle.message("xml.inspections.unused.schema.location"),
                                                   new RemoveNamespaceDeclarationFix(declaredPrefix, true, true));
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
    Document document = file.getViewProvider().getDocument();
    assert document != null;
    documentManager.commitDocument(document);
    String trimmed = element.getValue().trim();
    XmlAttribute pointerElement = pointer.getElement();
    assert pointerElement != null;
    pointerElement.setValue(trimmed);
  }

  private static void checkUnusedLocations(XmlAttribute attribute, ProblemsHolder holder, @NotNull XmlRefCountHolder refCountHolder) {
    if (XmlUtil.XML_SCHEMA_INSTANCE_URI.equals(attribute.getNamespace())) {
      if (XmlUtil.NO_NAMESPACE_SCHEMA_LOCATION_ATT.equals(attribute.getLocalName())) {
        if (refCountHolder.isInUse("")) return;
        holder.registerProblem(attribute, XmlAnalysisBundle.message("xml.inspections.unused.schema.location"), ProblemHighlightType.LIKE_UNUSED_SYMBOL,
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
            if (ArrayUtil.indexOf(attribute.getParent().knownNamespaces(), ns) == -1 && !refCountHolder.isUsedNamespace(ns)) {
              if (!XmlHighlightVisitor.hasBadResolve(reference, false)) {
                holder.registerProblemForReference(reference, ProblemHighlightType.LIKE_UNUSED_SYMBOL, XmlAnalysisBundle.message("xml.inspections.unused.schema.location"),
                                                   new RemoveNamespaceLocationFix(ns));
              }
              for (int j = i + 1; j < referencesLength; j++) {
                PsiReference nextRef = references[j];
                if (nextRef instanceof URLReference) break;
                if (!XmlHighlightVisitor.hasBadResolve(nextRef, false)) {
                  holder.registerProblemForReference(nextRef, ProblemHighlightType.LIKE_UNUSED_SYMBOL, XmlAnalysisBundle.message("xml.inspections.unused.schema.location"),
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
    return value == null ? PsiReference.EMPTY_ARRAY : getLocationReferences(namespace, value);
  }

  private static PsiReference[] getLocationReferences(String namespace, XmlAttributeValue value) {
    PsiReference[] references = value.getReferences();
    for (int i = 0, referencesLength = references.length; i < referencesLength; i+=2) {
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

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "XmlUnusedNamespaceDeclaration";
  }

  public static class RemoveNamespaceDeclarationFix implements LocalQuickFix {
    protected final String myPrefix;
    private final boolean myLocationFix;
    private final boolean myRemoveLocation;

    private RemoveNamespaceDeclarationFix(@Nullable String prefix, boolean locationFix, boolean removeLocation) {
      myPrefix = prefix;
      myLocationFix = locationFix;
      myRemoveLocation = removeLocation;
    }

    @Override
    @NotNull
    public String getName() {
      return getFamilyName();
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return XmlAnalysisBundle.message("xml.quickfix.remove.unused.namespace.decl");
    }

    @Override
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

      SmartPsiElementPointer<XmlTag> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(parent);

      doRemove(project, attribute, parent);

      if (reformat) {
        reformatStartTag(project, pointer);
      }
      return pointer;
    }

    public static void reformatStartTag(Project project, SmartPsiElementPointer<? extends XmlTag> pointer) {
      PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
      PsiFile file = pointer.getContainingFile();
      assert file != null;
      Document document = file.getViewProvider().getDocument();
      assert document != null;
      manager.commitDocument(document);
      XmlTag tag = pointer.getElement();
      assert tag != null;
      XmlUtil.reformatTagStart(tag);
    }

    protected void doRemove(Project project, XmlAttribute attribute, XmlTag parent) {
      if (!attribute.isNamespaceDeclaration()) {
        SchemaPrefix schemaPrefix = DefaultXmlExtension.DEFAULT_EXTENSION.getPrefixDeclaration(parent, myPrefix);
        if (schemaPrefix != null) {
          attribute = schemaPrefix.getDeclaration();
        }
        else {
          // declaration was already removed by previous fix in "Fix all"
          return;
        }
      }
      String namespace = attribute.getValue();
      String prefix = getDeclaredPrefix(attribute);

      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      Document document = parent.getContainingFile().getViewProvider().getDocument();
      assert document != null;
      attribute.delete();
      if (myRemoveLocation) {
        if (prefix.isEmpty()) {
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
    }

    public static void removeReferenceText(PsiReference ref) {
      PsiElement element = ref.getElement();
      PsiFile file = element.getContainingFile();
      TextRange range = ref.getRangeInElement().shiftRight(element.getTextRange().getStartOffset());
      Document document = file.getViewProvider().getDocument();
      assert document != null;
      PsiDocumentManager.getInstance(file.getProject()).doPostponedOperationsAndUnblockDocument(document);
      document.deleteString(range.getStartOffset(), range.getEndOffset());
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof RemoveNamespaceDeclarationFix &&
             Objects.equals(myPrefix, ((RemoveNamespaceDeclarationFix)obj).myPrefix) &&
             (myLocationFix || ((RemoveNamespaceDeclarationFix)obj).myLocationFix);
    }

    @Override
    public int hashCode() {
      return myPrefix == null ? 0 : myPrefix.hashCode();
    }
  }

  public static final class RemoveNamespaceLocationFix extends RemoveNamespaceDeclarationFix {

    private RemoveNamespaceLocationFix(String namespace) {
      super(namespace, true, true);
    }

    @NotNull
    @Override
    public String getName() {
      return XmlAnalysisBundle.message("xml.intention.remove.unused.namespace.location");
    }

    @Override
    protected void doRemove(Project project, XmlAttribute attribute, XmlTag parent) {
      if (StringUtil.isEmpty(myPrefix)) {
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

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }
  }
}
