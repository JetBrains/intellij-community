package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.TemplateStateListener;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.ListPopup;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public abstract class CreateFromUsageBaseAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseAction");

  protected CreateFromUsageBaseAction() {
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = getElement();
    if (element == null) {
      return false;
    }

    PsiClass[] targetClasses = getTargetClasses(element);
    if (targetClasses == null) {
      return false;
    }

    if (isValidElement(element)) {
      return false;
    }

    return isAvailableImpl(offset);
  }

  protected abstract boolean isAvailableImpl(int offset);

  protected abstract void invokeImpl(PsiClass targetClass);

  protected abstract boolean isValidElement(PsiElement result);

  protected boolean shouldShowTag(int offset, PsiElement namedElement, PsiElement element) {
    if (namedElement == null) return false;
    TextRange range = namedElement.getTextRange();
    if (range.getLength() == 0) return false;
    boolean isInNamedElement = offset >= range.getStartOffset() && offset <= range.getEndOffset();
    return isInNamedElement || offset >= element.getTextRange().getEndOffset();
  }

  public void invoke(Project project, final Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement element = getElement();

    if (LOG.isDebugEnabled()) {
      LOG.debug("CreateFromUsage: element =" + element);
    }

    if (element == null) {
      return;
    }

    PsiClass[] targetClasses = getTargetClasses(element);
    if (targetClasses == null) return;

    if (targetClasses.length == 1) {
      doInvoke(project, targetClasses[0]);
    } else {
      chooseTargetClass(targetClasses, editor);
    }
  }

  private void doInvoke(Project project, PsiClass targetClass) {
    if (!prepareTargetFile(targetClass.getContainingFile())) {
      return;
    }

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    invokeImpl(targetClass);
  }

  abstract protected PsiElement getElement();

  protected void chooseTargetClass(PsiClass[] classes, Editor editor) {
    final Project project = classes[0].getProject();

    String title = " Choose Target Class ";
    final JList list = new JList(classes);
    PsiElementListCellRenderer renderer = new PsiClassListCellRenderer();
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(renderer);
    renderer.installSpeedSearch(list);

    final Runnable runnable = new Runnable() {
      public void run() {
        int index = list.getSelectedIndex();
        if (index < 0) return;
        final PsiClass aClass = (PsiClass) list.getSelectedValue();
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            doInvoke(project, aClass);
          }
        });
      }
    };
    ListPopup listPopup = new ListPopup(title, list, runnable, project);
    LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
    Point caretLocation = editor.logicalPositionToXY(caretPosition);
    int x = caretLocation.x;
    int y = caretLocation.y;
    Point location = editor.getContentComponent().getLocationOnScreen();
    x += location.x;
    y += location.y;
    listPopup.show(x, y);
  }

  protected Editor positionCursor(final Project project, PsiFile targetFile, PsiElement element) {
    TextRange range = element.getTextRange();
    int textOffset = range.getStartOffset();

    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, targetFile.getVirtualFile(), textOffset);
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  protected void setupVisibility(PsiClass parentClass, PsiClass targetClass, PsiModifierList list) throws IncorrectOperationException {
    if (targetClass.isInterface()) {
      list.deleteChildRange(list.getFirstChild(), list.getLastChild());
      return;
    }

    if (parentClass != null && (parentClass.equals(targetClass) || PsiTreeUtil.isAncestor(targetClass, parentClass, true))) {
      list.setModifierProperty(PsiModifier.PRIVATE, true);
    } else {
      list.setModifierProperty(PsiModifier.PUBLIC, true);
    }
  }

  protected boolean shouldCreateStaticMember(PsiReferenceExpression ref, PsiElement enclosingContext, PsiClass targetClass) {
    if (targetClass.isInterface()) {
      return false;
    }

    PsiExpression qualifierExpression = ref.getQualifierExpression();
    while (qualifierExpression instanceof PsiParenthesizedExpression) {
      qualifierExpression = ((PsiParenthesizedExpression) qualifierExpression).getExpression();
    }

    if (qualifierExpression instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression) qualifierExpression;

      final PsiElement resolvedElement = referenceExpression.resolve();

      if (resolvedElement instanceof PsiClass) {
        return true;
      } else {
        return false;
      }
    } else if (qualifierExpression instanceof PsiTypeCastExpression) {
      return false;
    } else if (qualifierExpression instanceof PsiCallExpression) {
      return false;
    } else {
      if (enclosingContext instanceof PsiMethod) {
        PsiMethodCallExpression callExpression;

        if (ref.getParent() instanceof PsiMethodCallExpression) {
          callExpression = PsiTreeUtil.getParentOfType(ref.getParent(), PsiMethodCallExpression.class);
        } else {
          callExpression = PsiTreeUtil.getParentOfType(ref, PsiMethodCallExpression.class);
        }

        if (callExpression != null && callExpression.getMethodExpression().getText().equals("super")) {
          return true;
        }

        PsiMethod method = (PsiMethod) enclosingContext;
        return method.hasModifierProperty(PsiModifier.STATIC);
      } else if (enclosingContext instanceof PsiField) {
        PsiField field = (PsiField) enclosingContext;
        return field.hasModifierProperty(PsiModifier.STATIC);
      } else if (enclosingContext instanceof PsiClassInitializer) {
        PsiClassInitializer initializer = (PsiClassInitializer) enclosingContext;
        return initializer.hasModifierProperty(PsiModifier.STATIC);
      }
    }

    return false;
  }

  private PsiExpression getQualifier (PsiElement element) {
    if (element instanceof PsiNewExpression) {
      PsiJavaCodeReferenceElement ref = ((PsiNewExpression) element).getClassReference();
      if (ref instanceof PsiReferenceExpression) {
        return ((PsiReferenceExpression) ref).getQualifierExpression();
      }
    } else if (element instanceof PsiReferenceExpression) {
      return ((PsiReferenceExpression) element).getQualifierExpression();
    } else if (element instanceof PsiMethodCallExpression) {
      return ((PsiMethodCallExpression) element).getMethodExpression().getQualifierExpression();
    }

    return null;
  }

  public PsiSubstitutor getTargetSubstitutor (PsiElement element) {
    if (element instanceof PsiNewExpression) {
      PsiSubstitutor substitutor = ((PsiNewExpression)element).getClassReference().advancedResolve(false).getSubstitutor();
      return substitutor == null ? PsiSubstitutor.EMPTY : substitutor;
    }

    PsiExpression qualifier = getQualifier(element);
    if (qualifier != null) {
      PsiType type = qualifier.getType();
      if (type instanceof PsiClassType) {
        return ((PsiClassType)type).resolveGenerics().getSubstitutor();
      }
    }

    return PsiSubstitutor.EMPTY;
  }

  //Should return only valid inproject classes
  protected PsiClass[] getTargetClasses(PsiElement element) {
    PsiClass psiClass = null;
    PsiExpression qualifier = null;
    if (element instanceof PsiNewExpression) {
      PsiJavaCodeReferenceElement ref = ((PsiNewExpression) element).getClassReference();
      if (ref instanceof PsiReferenceExpression) {
        qualifier = ((PsiReferenceExpression) ref).getQualifierExpression();
      } else if (ref != null) {
        PsiElement refElement = ref.resolve();
        if (refElement instanceof PsiClass) psiClass = (PsiClass) refElement;
      }
    } else if (element instanceof PsiReferenceExpression) {
      qualifier = ((PsiReferenceExpression) element).getQualifierExpression();
    } else if (element instanceof PsiMethodCallExpression) {
      qualifier = ((PsiMethodCallExpression) element).getMethodExpression().getQualifierExpression();
    }
    if (qualifier != null) {
      PsiType type = qualifier.getType();
      if (type instanceof PsiClassType) {
        psiClass = PsiUtil.resolveClassInType(type);
      } else if (qualifier instanceof PsiReferenceExpression) {
        PsiElement refElement = ((PsiReferenceExpression) qualifier).resolve();

        if (refElement instanceof PsiClass) {
          psiClass = (PsiClass) refElement;
        }
      }
    } else if (psiClass == null) {
      psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }

    if (!(element instanceof PsiMethodCallExpression)) {
      while (psiClass instanceof PsiAnonymousClass) {
        psiClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class);
      }
    }
    if (!(psiClass instanceof PsiTypeParameter)) {
      return psiClass != null && psiClass.isValid() && psiClass.getManager().isInProject(psiClass) ? new PsiClass[]{psiClass} : null;
    } else {
      PsiClass[] supers = psiClass.getSupers();
      List<PsiClass> filtered = new ArrayList<PsiClass>();
      for (int i = 0; i < supers.length; i++) {
        PsiClass aSuper = supers[i];
        if (!aSuper.isValid() || !aSuper.getManager().isInProject(aSuper)) continue;
        if (!(aSuper instanceof PsiTypeParameter)) filtered.add(aSuper);
      }
      return filtered.size() > 0 ? filtered.toArray(new PsiClass[filtered.size()]) : null;
    }
  }

  protected boolean isNonqualifiedReference(PsiElement result) {
    return result instanceof PsiJavaCodeReferenceElement &&
        !((PsiJavaCodeReferenceElement) result).isQualified();
  }

  protected void startTemplate (final Editor editor, final Template template, final Project project) {
    Runnable runnable = new Runnable() {
      public void run() {
        TemplateManager.getInstance(project).startTemplate(editor, template);
      }
    };
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
    else {
      runnable.run();
    }
  }

  protected void startTemplate (final Editor editor, final Template template, final Project project, final TemplateStateListener listener) {
    Runnable runnable = new Runnable() {
      public void run() {
        TemplateManager.getInstance(project).startTemplate(editor, template, listener);
      }
    };
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
    else {
      runnable.run();
    }
  }
}
