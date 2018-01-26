// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RenameXmlAttributeProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameXmlAttributeProcessor");

  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return element instanceof XmlAttribute || element instanceof XmlAttributeValue;
  }

  @Override
  public void renameElement(@NotNull final PsiElement element,
                            @NotNull final String newName,
                            @NotNull final UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    if (element instanceof XmlAttribute) {
      doRenameXmlAttribute((XmlAttribute)element, newName, listener);
    }
    else if (element instanceof XmlAttributeValue) {
      doRenameXmlAttributeValue((XmlAttributeValue)element, newName, usages, listener);
    }
  }

  private static void doRenameXmlAttribute(XmlAttribute attribute,
                                           String newName,
                                           @Nullable RefactoringElementListener listener) {
    try {
      final PsiElement element = attribute.setName(newName);
      if (listener != null) {
        listener.elementRenamed(element);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void doRenameXmlAttributeValue(@NotNull XmlAttributeValue value,
                                                String newName,
                                                UsageInfo[] infos,
                                                @Nullable RefactoringElementListener listener)
    throws IncorrectOperationException {
    LOG.assertTrue(value.isValid());

    renameAll(value, infos, newName, value.getValue());

    PsiManager psiManager = value.getManager();
    LOG.assertTrue(psiManager != null);
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(psiManager.getProject()).createFileFromText("dummy.xml", XMLLanguage.INSTANCE, "<a attr=\"" + newName + "\"/>");
    @SuppressWarnings("ConstantConditions")
    PsiElement element = value.replace(file.getRootTag().getAttributes()[0].getValueElement());
    if (listener != null) {
      listener.elementRenamed(element);
    }
  }

  private static void renameAll(PsiElement originalElement, UsageInfo[] infos, String newName,
                                String originalName) throws IncorrectOperationException {
    if (newName.equals(originalName)) return;
    Queue<PsiReference> queue = new Queue<>(infos.length);
    for (UsageInfo info : infos) {
      if (info.getElement() == null) continue;
      PsiReference ref = info.getReference();
      if (ref == null) continue;
      queue.addLast(ref);
    }

    while(!queue.isEmpty()) {
      final PsiReference reference = queue.pullFirst();
      final PsiElement oldElement = reference.getElement();
      if (!oldElement.isValid() || oldElement == originalElement) continue;
      final PsiElement newElement = reference.handleElementRename(newName);
      if (!oldElement.isValid()) {
        for (PsiReference psiReference : ReferencesSearch.search(originalElement, new LocalSearchScope(newElement), false)) {
          queue.addLast(psiReference);
        }
      }
    }
  }

}
