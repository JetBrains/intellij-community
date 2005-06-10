
package com.intellij.codeInsight.daemon.impl;

import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.ListPopup;
import com.intellij.uiDesigner.editor.UIFormEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;

class LineMarkerNavigator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.LineMarkerNavigator");

  public static void browse(MouseEvent e, LineMarkerInfo info) {
    PsiElement element = info.elementRef.get();
    if (element == null || !element.isValid()) return;

    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (info.type == LineMarkerInfo.OVERRIDING_METHOD){
        PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method, false);
        if (superMethods.length == 0) return;
        boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(superMethods);
        openTargets(e, superMethods, "Choose Super Method of " + method.getName(), new MethodCellRenderer(showMethodNames));
      }
      else if (info.type == LineMarkerInfo.OVERRIDEN_METHOD){
        PsiManager manager = method.getManager();
        PsiSearchHelper helper = manager.getSearchHelper();
        Project project = manager.getProject();
        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        PsiMethod[] overridings = helper.findOverridingMethods(method, scope, true);
        if (overridings.length == 0) return;
        String title = method.hasModifierProperty(PsiModifier.ABSTRACT) ?  "Choose Implementation of " : "Choose Overriding Method of ";
        title += method.getName();
        boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(overridings);
        MethodCellRenderer renderer = new MethodCellRenderer(showMethodNames);
        Arrays.sort(overridings, renderer.getComparator());
        openTargets(e, overridings, title, renderer);
      }
      else{
        LOG.assertTrue(false);
      }
    }
    else if (element instanceof PsiClass) {
      PsiClass aClass = (PsiClass) element;
      PsiManager manager = aClass.getManager();
      PsiSearchHelper helper = manager.getSearchHelper();
      if (info.type == LineMarkerInfo.SUBCLASSED_CLASS){
        GlobalSearchScope scope = GlobalSearchScope.allScope(manager.getProject());
        PsiClass[] inheritors = helper.findInheritors(aClass, scope, true);
        if (inheritors.length == 0) return;
        String title = aClass.isInterface() ?  "Choose Implementation of " : "Choose Subclass of ";
        title += aClass.getName();
        PsiClassListCellRenderer renderer = new PsiClassListCellRenderer();
        Arrays.sort(inheritors, renderer.getComparator());
        openTargets(e, inheritors, title, renderer);
      } else if (info.type == LineMarkerInfo.BOUND_CLASS_OR_FIELD) {
        openFormFile(helper, aClass, null, manager);
      }
    }
    else if (element instanceof PsiField) {
      PsiField field = (PsiField)element;
      PsiClass aClass = field.getContainingClass();
      PsiManager manager = aClass.getManager();
      PsiSearchHelper helper = manager.getSearchHelper();
      openFormFile(helper, aClass, field.getName(), manager);
    }
  }

  private static void openFormFile(PsiSearchHelper helper, PsiClass aClass, String field, PsiManager manager) {
    if (aClass.getQualifiedName() != null) {
      PsiFile[] formFiles = helper.findFormsBoundToClass(aClass.getQualifiedName());
      if (formFiles.length == 0) return;
      VirtualFile virtualFile = formFiles[0].getVirtualFile();
      Project project = manager.getProject();
      FileEditor[] editors = FileEditorManager.getInstance(project).openFile(virtualFile, true);
      if (field != null) {
        for (FileEditor editor : editors) {
          if (editor instanceof UIFormEditor) {
            ((UIFormEditor)editor).selectComponent(field);
          }
        }
      }
    }
  }

  private static void openTargets(MouseEvent e, PsiMember[] targets, String title, ListCellRenderer listRenderer) {
    if (targets.length == 0) return;
    Project project = targets[0].getProject();
    if (targets.length == 1){
      targets[0].navigate(true);
    }
    else{
      final JList list = new JList(targets);
      list.setCellRenderer(listRenderer);
      ListPopup listPopup = new ListPopup(
        title,
        list,
        new Runnable() {
          public void run() {
            int[] ids = list.getSelectedIndices();
            if (ids == null || ids.length == 0) return;
            Object [] selectedElements = list.getSelectedValues();
            for (Object element : selectedElements) {
              PsiElement selected = (PsiElement) element;
              LOG.assertTrue(selected.isValid());
              ((PsiMember)selected).navigate(true);
            }             
          }
        },
        project
      );

      MouseEvent mouseEvent = e;
      Point p = new Point(mouseEvent.getPoint());
      SwingUtilities.convertPointToScreen(p, mouseEvent.getComponent());

      listPopup.show(p.x, p.y);
    }
  }
}