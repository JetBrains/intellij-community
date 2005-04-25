package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ModifierFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.ModifierFix");

  private final PsiModifierList myModifierList;
  private final String myModifier;
  private final boolean myShouldHave;
  private boolean myShowContainingClass;

  public ModifierFix(PsiModifierList modifierList, String modifier, boolean shouldHave) {
    myModifierList = modifierList;
    myModifier = modifier;
    myShouldHave = shouldHave;
  }

  public ModifierFix(PsiModifierListOwner owner, String modifier, boolean shouldHave) {
    this(owner, modifier, shouldHave, false);
  }

  public ModifierFix(PsiModifierListOwner owner, String modifier, boolean shouldHave, boolean showContainingClass) {
    this(owner.getModifierList(), modifier, shouldHave);
    myShowContainingClass = showContainingClass;
  }

  public String getText() {
    String name = null;
    PsiElement parent = myModifierList.getParent();
    if (parent instanceof PsiClass) {
      name = ((PsiClass)parent).getName();
    }
    else if (parent instanceof PsiMethod) {
      name = PsiFormatUtil.formatMethod((PsiMethod)parent,
                                        PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME |
                                                              (myShowContainingClass
                                                               ? PsiFormatUtil.SHOW_CONTAINING_CLASS
                                                               : 0),
                                        0);
    }
    else if (parent instanceof PsiVariable) {
      name =
      PsiFormatUtil.formatVariable((PsiVariable)parent,
                                   PsiFormatUtil.SHOW_NAME |
                                   (myShowContainingClass ? PsiFormatUtil.SHOW_CONTAINING_CLASS : 0),
                                   PsiSubstitutor.EMPTY);
    }
    else if (parent instanceof PsiClassInitializer) {
      PsiClass containingClass = ((PsiClassInitializer)parent).getContainingClass();
      String className = containingClass instanceof PsiAnonymousClass ?
                               "Anonymous class derived from " +
                               ((PsiAnonymousClass)containingClass).getBaseClassType().getPresentableText()
                               : containingClass.getName();
      name = className + " class initializer";
    }
    String text = MessageFormat.format("Make ''{0}'' {1}{2}",
                                             new Object[]{
                                               name,
                                               (myShouldHave ? "" : "not "),
                                               myModifier.equals(PsiModifier.PACKAGE_LOCAL)
                                               ? "package local"
                                               : myModifier,
                                             });
    return text;
  }

  public String getFamilyName() {
    return "Fix Modifiers";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myModifierList != null
           && myModifierList.isValid()
           && myModifierList.getManager().isInProject(myModifierList)
           && myModifier != null;
  }

  private void changeModifierList (PsiModifierList modifierList) {
    try {
      modifierList.setModifierProperty(myModifier, myShouldHave);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public void invoke(Project project, Editor editor, final PsiFile file) {

    final List<PsiModifierList> modifiersList = new ArrayList<PsiModifierList>();
    PsiElement owner = myModifierList.getParent();
    if (owner instanceof PsiMethod) {
      PsiSearchHelper helper = PsiManager.getInstance(project).getSearchHelper();
      PsiModifierList copy = (PsiModifierList)myModifierList.copy();
      changeModifierList(copy);
      final int accessLevel = PsiUtil.getAccessLevel(copy);

      helper.processOverridingMethods(new PsiElementProcessor<PsiMethod>() {
        public boolean execute(PsiMethod element) {
          PsiMethod inheritor = element;
          PsiModifierList list = inheritor.getModifierList();
          if (element.getManager().isInProject(element) && PsiUtil.getAccessLevel(list) < accessLevel) {
            modifiersList.add(list);
          }
          return true;
        }
      }, (PsiMethod)owner, owner.getResolveScope(), true);
    }

    if (!CodeInsightUtil.prepareFileForWrite(myModifierList.getContainingFile())) return;

    if (modifiersList.size() > 0) {
      if (Messages.showYesNoDialog(project,
                                   "Do you want to change inheritors' visibility to visibility of the base method?",
                                   "Change Inheritors", Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            for (Iterator<PsiModifierList> iterator = modifiersList.iterator(); iterator.hasNext();) {
              changeModifierList(iterator.next());
            }
          }
        });
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        changeModifierList(myModifierList);
        QuickFixAction.markDocumentForUndo(file);
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }

}
