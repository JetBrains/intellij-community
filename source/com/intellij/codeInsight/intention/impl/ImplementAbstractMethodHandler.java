/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Sep 4, 2002
 * Time: 6:26:27 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.ui.ListPopup;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

public class ImplementAbstractMethodHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.ImplementAbstractMethodHandler");

  private final Project myProject;
  private final Editor myEditor;
  private final PsiMethod myMethod;
  private JList myList;
  private Point myLocation;

  public ImplementAbstractMethodHandler(Project project, Editor editor, PsiMethod method) {
    myProject = project;
    myEditor = editor;
    myMethod = method;
    myLocation = myEditor.getContentComponent().getLocationOnScreen();
  }

  public void invoke() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final PsiClass[][] result = new PsiClass[1][];
    ApplicationManager.getApplication().runProcessWithProgressSynchronously(
      new Runnable() {
        public void run() {
          final PsiClass psiClass = myMethod.getContainingClass();
          if (!psiClass.isValid()) return;
          result[0] = getClassImplementations(psiClass);
        }
      },
      "Searching For Descendants...",
      true,
      myProject
    );

    if (result[0] == null) return;

    if (result[0].length == 0) {
      Messages.showMessageDialog(myProject, "There are no classes found where this method can be implemented", "No Classes Found", Messages.getInformationIcon());
      return;
    }

    if (result[0].length == 1) {
      implementInClass(result[0][0]);
      return;
    }

    PsiClassListCellRenderer renderer = new PsiClassListCellRenderer();
    Arrays.sort(result[0], renderer.getComparator());

    myList = new JList(result[0]);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(renderer);
    renderer.installSpeedSearch(myList);

    final Runnable runnable = new Runnable(){
      public void run() {
        int index = myList.getSelectedIndex();
        if (index < 0) return;
        PsiElement element = (PsiElement)myList.getSelectedValue();
        implementInClass((PsiClass)element);
      }
    };

    ListPopup listPopup = new ListPopup(" Choose Implementing Class ", myList, runnable, myProject);
    LogicalPosition caretPosition = myEditor.getCaretModel().getLogicalPosition();
    Point caretLocation = myEditor.logicalPositionToXY(caretPosition);
    int x = caretLocation.x;
    int y = caretLocation.y;
    x += myLocation.x;
    y += myLocation.y;
    listPopup.show(x, y);
  }

  private void implementInClass(final PsiClass psiClass) {
    if (!psiClass.isValid()) return;
    if (!psiClass.isWritable()) {
      MessagesEx.fileIsReadOnly(myProject, psiClass.getContainingFile().getVirtualFile()).showNow();
      PsiDocumentManager.getInstance(myProject).getDocument(psiClass.getContainingFile()).fireReadOnlyModificationAttempt();
      return;
    }

    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              BaseIntentionAction.prepareTargetFile(psiClass.getContainingFile());
              OverrideImplementUtil.overrideOrImplement(psiClass, myMethod);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, "Implement method", null);
  }

  private PsiClass[] getClassImplementations(final PsiClass psiClass) {
    ArrayList<PsiClass> list = new ArrayList<PsiClass>();
    PsiManager manager = psiClass.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
    PsiClass[] inheritors = helper.findInheritors(psiClass, projectScope, true);
    for (int i = 0; i < inheritors.length; i++) {
      PsiClass inheritor = inheritors[i];
      if (!inheritor.isInterface()) {
        PsiMethod method = inheritor.findMethodBySignature(myMethod, true);
        if (method == null || !method.getContainingClass().equals(psiClass)) continue;
        list.add(inheritor);
      }
    }

    return list.toArray(new PsiClass[list.size()]);
  }
}
