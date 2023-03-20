// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.actions.generate;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions.ActionDescription;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.serialization.ClassUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class DefaultGenerateElementProvider<T extends DomElement> extends GenerateDomElementProvider<T> {
  private final Class<T> myChildElementClass;

  public DefaultGenerateElementProvider(@ActionText String text, @ActionDescription String description,
                                        Class<T> childElementClass) {
    super(text, description);
    myChildElementClass = childElementClass;
  }

  @Override
  @Nullable
  public T generate(final Project project, final Editor editor, final PsiFile file) {
    return generate(getParentDomElement(project, editor, file), editor);
  }

  @Nullable
  protected abstract DomElement getParentDomElement(final Project project, final Editor editor, final PsiFile file);

  @SuppressWarnings({"unchecked"})
  @Nullable
  public T generate(@Nullable final DomElement parent, final Editor editor) {
    if (parent == null) {
      return null;
    }

    final List<? extends DomCollectionChildDescription> list = parent.getGenericInfo().getCollectionChildrenDescriptions();

    for (DomCollectionChildDescription childDescription : list) {
      if (ClassUtil.getRawType(childDescription.getType()).isAssignableFrom(myChildElementClass)) {
        XmlTag parentTag = parent.getXmlTag();
        if (editor != null && parentTag != null) {
          int offset = editor.getCaretModel().getOffset();
          PsiFile file = parentTag.getContainingFile();
          PsiElement psiElement = file.findElementAt(offset);

          if (psiElement instanceof PsiWhiteSpace && PsiTreeUtil.isAncestor(parentTag, psiElement, false)) {
              Document document = editor.getDocument();
            XmlTag childTag = parentTag.createChildTag(childDescription.getXmlElementName(), null, null, true);
            String text = childTag.getText();
            document.insertString(offset, text);
            Project project = editor.getProject();
            assert project != null;
            PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
            documentManager.commitDocument(document);
            PsiElement element = file.findElementAt(offset + 1);
            T domElement = DomUtil.findDomElement(element, myChildElementClass);
            if (domElement != null) return domElement;
            document.deleteString(offset, offset + text.length());
            documentManager.commitDocument(document);
          }
        }

        int index = getCollectionIndex(parent, childDescription, editor);

        return index < 0
               ? (T)childDescription.addValue(parent, myChildElementClass)
               : (T)childDescription.addValue(parent, myChildElementClass, index);
      }
    }
    return null;
  }

  private static int getCollectionIndex(final DomElement parent,
                                        final DomCollectionChildDescription childDescription,
                                        final Editor editor) {
    int offset = editor.getCaretModel().getOffset();

    for (int i = 0; i < childDescription.getValues(parent).size(); i++) {
      DomElement element = childDescription.getValues(parent).get(i);
      XmlElement xmlElement = element.getXmlElement();
      if (xmlElement != null && xmlElement.getTextRange().getStartOffset() >= offset) {
        return i;
      }
    }

    return -1;
  }
}
