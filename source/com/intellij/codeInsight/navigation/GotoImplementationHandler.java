package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.ListPopup;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

public class GotoImplementationHandler implements CodeInsightActionHandler {
  protected interface ResultsFilter {
    boolean acceptClass(PsiClass aClass);

    boolean acceptMethod(PsiMethod method);
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    int flags = TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
                | TargetElementUtil.ELEMENT_NAME_ACCEPTED
                | TargetElementUtil.LOOKUP_ITEM_ACCEPTED
                | TargetElementUtil.THIS_ACCEPTED
                | TargetElementUtil.SUPER_ACCEPTED;
    final PsiElement element = TargetElementUtil.findTargetElement(editor, flags);
    if (!(element instanceof PsiMethod) && !(element instanceof PsiClass)) {
      return;
    }

    PsiElement[] result = searchImplementations(editor, file, element, false);
    if (result != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.goto.implementation");
      show(project, editor, element, result);
    }
  }

  public PsiElement[] searchImplementations(Editor editor, PsiFile file, final PsiElement element, boolean includeSelf) {
    final PsiElement[][] result = new PsiElement[1][];
    if (!ApplicationManager.getApplication().runProcessWithProgressSynchronously(
      new Runnable() {
      public void run() {
        result[0] = getSearchResults(element);
      }
    },
      CodeInsightBundle.message("searching.for.implementations"),
      true,
      element.getProject())) {
      return null;
    }

    if (result[0] != null && result[0].length > 0) {
      if (!includeSelf) return filterElements(editor, file, element, result[0]);
      PsiElement[] all = new PsiElement[result[0].length + 1];
      all[0] = element;
      System.arraycopy(result[0], 0, all, 1, result[0].length);
      return filterElements(editor, file, element, all);
    }
    return includeSelf ? new PsiElement[] {element} : PsiElement.EMPTY_ARRAY;
  }

  public boolean startInWriteAction() {
    return false;
  }

  protected ResultsFilter createFilter(Project project, final Editor editor, final PsiFile file, PsiElement element) {
    final PsiElement element1 = TargetElementUtil.findTargetElement(editor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);

    return new ResultsFilter() {
      public boolean acceptClass(PsiClass aClass) {
        return aClass != element1;
      }

      public boolean acceptMethod(PsiMethod method) {
        return method != element1;
      }
    };
  }

  private static void getOverridingMethods(PsiMethod method, ArrayList<PsiMethod> list) {
    if (!method.hasModifierProperty(PsiModifier.FINAL)) {
      PsiManager manager = method.getManager();
      PsiSearchHelper helper = manager.getSearchHelper();
      GlobalSearchScope scope = GlobalSearchScope.allScope(manager.getProject());
      PsiMethod[] methods = helper.findOverridingMethods(method, scope, true);

      AddMethodLoop:
        for (int i = 0; i < methods.length; i++) {
          PsiMethod m = methods[i];
          PsiClass aClass = m.getContainingClass();
          if (aClass != null) {
            for (int j = 0; j < methods.length; j++) {
              if (j == i) continue;
              PsiMethod method1 = methods[j];
              PsiClass aClass1 = method1.getContainingClass();
              if (aClass1 != null) {
                if (aClass.isInheritor(aClass1, true)) continue AddMethodLoop;
              }
            }
          }
          if (!list.contains(m)) {
            list.add(m);
            getOverridingMethods(m, list);
          }
        }
    }

  }

  protected PsiElement[] filterElements(Editor editor, PsiFile file, PsiElement element, PsiElement[] targetElements) {
    if (targetElements.length <= 1) return targetElements;
    Project project = file.getProject();
    ResultsFilter filter = createFilter(project, editor, file, element);

    ArrayList<PsiElement> result = new ArrayList<PsiElement>();
    for (PsiElement targetElement : targetElements) {
      if (targetElement instanceof PsiClass && filter.acceptClass((PsiClass)targetElement)) {
        result.add(targetElement);
      }
      else if (targetElement instanceof PsiMethod && filter.acceptMethod((PsiMethod)targetElement)) {
        result.add(targetElement);
      }
    }

    if (result.size() == targetElements.length) {
      return targetElements;
    }
    else {
      return result.toArray(new PsiElement[result.size()]);
    }
  }

  private PsiElement[] getSearchResults(PsiElement sourceElement) {
    if (sourceElement instanceof PsiMethod) {
      return getMethodImplementations((PsiMethod) sourceElement);
    }
    else if (sourceElement instanceof PsiClass) {
      return getClassImplementations((PsiClass) sourceElement);
    }
    else {
      throw new IllegalArgumentException(sourceElement == null ? null : sourceElement.getClass().getName());
    }
  }

  private static PsiElement[] getClassImplementations(final PsiClass psiClass) {
    final ArrayList<PsiClass> list = new ArrayList<PsiClass>();

    PsiSearchHelper helper = psiClass.getManager().getSearchHelper();
    GlobalSearchScope searchScope = GlobalSearchScope.allScope(psiClass.getProject());
    helper.processInheritors(new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass element) {
        if (!element.isInterface()) {
          list.add(element);
        }
        return true;
      }
    }, psiClass, searchScope, true);

    if (!psiClass.isInterface()) {
      list.add(psiClass);
    }

    return list.toArray(new PsiElement[list.size()]);
  }

  private static PsiElement[] getMethodImplementations(final PsiMethod method) {
    ArrayList<PsiMethod> result = new ArrayList<PsiMethod>();

    getOverridingMethods(method, result);
    if (!method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      result.add(0, method);
    }

    return result.toArray(new PsiElement[result.size()]);
  }

  private static void show(final Project project, Editor editor, final PsiElement sourceElement, final PsiElement[] elements) {
    if (elements == null || elements.length == 0) {
      return;
    }

    if (elements.length == 1) {
      Navigatable descriptor = EditSourceUtil.getDescriptor(elements[0]);
      if (descriptor != null && descriptor.canNavigate()) {
        descriptor.navigate(true);
      }
    }
    else {
      PsiElementListCellRenderer renderer = sourceElement instanceof PsiMethod
                                            ? new MethodCellRenderer(!PsiUtil.allMethodsHaveSameSignature(Arrays.asList(elements).toArray(PsiMethod.EMPTY_ARRAY)))
                                            : (PsiElementListCellRenderer)new PsiClassListCellRenderer();

      Arrays.sort(elements, renderer.getComparator());

      final JList list = new JList(elements);
      list.setCellRenderer(renderer);

      renderer.installSpeedSearch(list);

      final Runnable runnable = new Runnable() {
        public void run() {
          int[] ids = list.getSelectedIndices();
          if (ids == null || ids.length == 0) return;
          Object [] selectedElements = list.getSelectedValues();
          for (Object element : selectedElements) {
            Navigatable descriptor = EditSourceUtil.getDescriptor((PsiElement)element);
            if (descriptor != null && descriptor.canNavigate()) {
              descriptor.navigate(true);
            }
          }
        }
      };

      String title = CodeInsightBundle.message("goto.implementation.chooser.title", ((PsiNamedElement)sourceElement).getName());
      ListPopup listPopup = new ListPopup(title, list, runnable, project);
      LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
      Point caretLocation = editor.logicalPositionToXY(caretPosition);
      int x = caretLocation.x;
      int y = caretLocation.y;
      Point editorLocation = editor.getContentComponent().getLocationOnScreen();
      x += editorLocation.x;
      y += editorLocation.y;
      listPopup.show(x, y);
    }
  }
}
