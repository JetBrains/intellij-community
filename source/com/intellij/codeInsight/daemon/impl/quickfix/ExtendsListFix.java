package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

public class ExtendsListFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.ExtendsListFix");

  final PsiClass myClass;
  final PsiClass myClassToExtendFrom;
  private final boolean myToAdd;
  private final PsiClassType myTypeToExtendFrom;

  public ExtendsListFix(PsiClass aClass, PsiClassType typeToExtendFrom, boolean toAdd) {
    myClass = aClass;
    myClassToExtendFrom = typeToExtendFrom.resolve();
    myTypeToExtendFrom = typeToExtendFrom;
    myToAdd = toAdd;
  }

  public String getText() {
    final String text = MessageFormat.format("Make ''{0}'' {1}{2} ''{3}''",
        new Object[]{
          myClass.getName(),
          (myToAdd ? "" : "not "),
          (myClass.isInterface() == myClassToExtendFrom.isInterface() ? "extend" : "implement"),
          myClassToExtendFrom.getQualifiedName(),
        });
    return text;
  }

  public String getFamilyName() {
    return "Extend Class from ";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return
        myClass != null
        && myClass.isValid()
        && myClass.getManager().isInProject(myClass)
        && myClassToExtendFrom != null
        && myClassToExtendFrom.isValid()
        && !myClassToExtendFrom.hasModifierProperty(PsiModifier.FINAL)
        && (myClassToExtendFrom.isInterface()
        || (!myClass.isInterface()
        && myClass.getExtendsList() != null
        && myClass.getExtendsList().getReferencedTypes() != null
        && myClass.getExtendsList().getReferencedTypes().length == 0))
        ;

  }

  protected void invokeImpl () {
    if (!CodeInsightUtil.prepareFileForWrite(myClass.getContainingFile())) return;
    final PsiReferenceList extendsList = myClass.isInterface() != myClassToExtendFrom.isInterface() ?
        myClass.getImplementsList() : myClass.getExtendsList();
    final PsiReferenceList otherList = myClass.isInterface() != myClassToExtendFrom.isInterface() ?
        myClass.getExtendsList() : myClass.getImplementsList();
    try {
      modifyList(extendsList, myToAdd, -1);
      modifyList(otherList, !myToAdd, -1);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    invokeImpl();
    QuickFixAction.spoilDocument(project, file);
  }

  /**
   * @param position to add new class to or -1 if add to the end
   */
  PsiReferenceList modifyList(final PsiReferenceList extendsList, boolean add, int position) throws IncorrectOperationException {
    final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
    boolean alreadyExtends = false;
    for (int i = 0; i < referenceElements.length; i++) {
      PsiJavaCodeReferenceElement referenceElement = referenceElements[i];
      if (referenceElement.resolve() == myClassToExtendFrom) {
        alreadyExtends = true;
        if (!add) {
          referenceElement.delete();
        }
      }
    }
    PsiReferenceList list = extendsList;
    if (add && !alreadyExtends) {
      final PsiElement anchor;
      if (position == -1) {
        anchor = referenceElements.length ==0 ? null : referenceElements[referenceElements.length-1];
      }
      else if (position == 0) {
        anchor = null;
      }
      else {
        anchor = referenceElements[position - 1];
      }
      final PsiJavaCodeReferenceElement classReferenceElement = myClass.getManager().getElementFactory().createReferenceElementByType(myTypeToExtendFrom);
      PsiElement element;
      if (anchor == null) {
        if (referenceElements.length == 0) {
          element = extendsList.add(classReferenceElement);
        }
        else {
          element = extendsList.addBefore(classReferenceElement, referenceElements[0]);
        }
      }
      else {
        element = extendsList.addAfter(classReferenceElement, anchor);
      }
      list = (PsiReferenceList) element.getParent();
    }
    return list;
  }

  public boolean startInWriteAction() {
    return true;
  }
}
