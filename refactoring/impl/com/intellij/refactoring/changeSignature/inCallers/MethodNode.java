package com.intellij.refactoring.changeSignature.inCallers;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * @author ven
 */
public class MethodNode extends CheckedTreeNode {
  private PsiMethod myMethod;
  private boolean myOldChecked;

  public MethodNode(final PsiMethod method) {
    super(method);
    myMethod = method;
    isChecked = false;
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  //IMPORTANT: do not build children in children()
  private void buildChildren () {
    if (children == null) {
      final PsiMethod[] callers = findCallers();
      children = new Vector(callers.length);
      for (PsiMethod caller : callers) {
        final MethodNode child = new MethodNode(caller);
        children.add(child);
        child.parent = this;
      }
    }
  }

  public TreeNode getChildAt(int index) {
    buildChildren();
    return super.getChildAt(index);
  }

  public int getChildCount() {
    buildChildren();
    return super.getChildCount();
  }

  public int getIndex(TreeNode aChild) {
    buildChildren();
    return super.getIndex(aChild);
  }

  private PsiMethod[] findCallers() {
    if (myMethod == null) return PsiMethod.EMPTY_ARRAY;
    final Project project = myMethod.getProject();
    final List<PsiMethod> callers = new ArrayList<PsiMethod>();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        final PsiSearchHelper searchHelper = PsiManager.getInstance(project).getSearchHelper();
        final PsiReference[] refs = searchHelper.findReferencesIncludingOverriding(myMethod, GlobalSearchScope.allScope(project), true);
        for (PsiReference ref : refs) {
          final PsiElement element = ref.getElement();
          if (!(element instanceof PsiReferenceExpression) ||
              !(((PsiReferenceExpression) element).getQualifierExpression() instanceof PsiSuperExpression)) {
            final PsiElement enclosingContext = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClass.class);
            if (enclosingContext instanceof PsiMethod &&
                !myMethod.equals(enclosingContext)) { //do not add recursive methods, TODO: mutually recursive methods
              callers.add((PsiMethod) enclosingContext);
            }
          }
        }
      }
    }, RefactoringBundle.message("caller.chooser.looking.for.callers"), false, project);
    return callers.toArray(new PsiMethod[callers.size()]);
  }

  public void customizeRenderer (ColoredTreeCellRenderer renderer) {
    if (myMethod == null) return;
    int flags = Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS;
    renderer.setIcon(myMethod.getIcon(flags));

    final StringBuffer buffer = new StringBuffer(128);
    final PsiClass containingClass = myMethod.getContainingClass();
    if (containingClass != null) {
      buffer.append(ClassPresentationUtil.getNameForClass(containingClass, false));
      buffer.append('.');
    }
    final String methodText = PsiFormatUtil.formatMethod(
      myMethod,
      PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE
    );
    buffer.append(methodText);

    final SimpleTextAttributes attributes = isEnabled() ?
                                            new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, UIUtil.getTreeForeground()) :
                                            SimpleTextAttributes.EXCLUDED_ATTRIBUTES;
    renderer.append(buffer.toString(), attributes);

    final String packageName = getPackageName(myMethod.getContainingClass());
    renderer.append("  (" + packageName + ")", new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC, Color.GRAY));
  }

  @Nullable
  private static String getPackageName(final PsiClass aClass) {
    final PsiFile file = aClass.getContainingFile();
    if (file instanceof PsiJavaFile) {
      return ((PsiJavaFile)file).getPackageName();
    }
    return null;
  }

  public void setEnabled(final boolean enabled) {
    super.setEnabled(enabled);
    if (!enabled) {
      myOldChecked = isChecked();
      setChecked(false);
    }
    else {
      setChecked(myOldChecked);
    }
  }
}
