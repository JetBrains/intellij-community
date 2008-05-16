package com.intellij.refactoring.rename;

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

public class RenameXmlAttributeProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameXmlAttributeProcessor");

  public boolean canProcessElement(final PsiElement element) {
    return element instanceof XmlAttribute || element instanceof XmlAttributeValue;
  }

  public void renameElement(final PsiElement element,
                            final String newName,
                            final UsageInfo[] usages,
                            final RefactoringElementListener listener) throws IncorrectOperationException {
    if (element instanceof XmlAttribute) {
      doRenameXmlAttribute((XmlAttribute)element, newName, listener);
    }
    else if (element instanceof XmlAttributeValue) {
      doRenameXmlAttributeValue((XmlAttributeValue)element, newName, usages, listener);
    }
  }

  private static void doRenameXmlAttribute(XmlAttribute attribute,
                                           String newName,
                                           RefactoringElementListener listener) {
    try {
      final PsiElement element = attribute.setName(newName);
      listener.elementRenamed(element);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void doRenameXmlAttributeValue(XmlAttributeValue value,
                                                String newName,
                                                UsageInfo[] infos,
                                                RefactoringElementListener listener)
    throws IncorrectOperationException {
    LOG.assertTrue(value != null);
    LOG.assertTrue(value.isValid());

    renameAll(value, infos, newName, value.getValue());

    PsiManager psiManager = value.getManager();
    LOG.assertTrue(psiManager != null);
    XmlFile file = (XmlFile)PsiFileFactory.getInstance(psiManager.getProject()).createFileFromText("dummy.xml", "<a attr=\"" + newName + "\"/>");
    final PsiElement element = value.replace(file.getDocument().getRootTag().getAttributes()[0].getValueElement());
    listener.elementRenamed(element);
  }

  private static void renameAll(PsiElement originalElement, UsageInfo[] infos, String newName,
                                String originalName) throws IncorrectOperationException {
    if (newName.equals(originalName)) return;
    Queue<PsiReference> queue = new Queue<PsiReference>(infos.length);
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
