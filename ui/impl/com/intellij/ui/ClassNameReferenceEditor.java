package com.intellij.ui;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class ClassNameReferenceEditor extends ReferenceEditorWithBrowseButton {
  private Project myProject;
  private PsiClass mySelectedClass;
  private String myChooserTitle;

  public ClassNameReferenceEditor(@NotNull final PsiManager manager, @Nullable final PsiClass selectedClass) {
    this(manager, selectedClass, null);
  }

  public ClassNameReferenceEditor(@NotNull final PsiManager manager, @Nullable final PsiClass selectedClass,
                                  @Nullable final GlobalSearchScope resolveScope) {
    super(null, manager.getProject(), new Function<String,Document>() {
      public Document fun(final String s) {
        PsiPackage defaultPackage = manager.findPackage("");
        final PsiCodeFragment fragment = manager.getElementFactory().createReferenceCodeFragment(s, defaultPackage, true, true);
        fragment.setVisibilityChecker(PsiCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
        if (resolveScope != null) {
          fragment.forceResolveScope(resolveScope);
        }
        return PsiDocumentManager.getInstance(manager.getProject()).getDocument(fragment);
      }
    }, selectedClass != null ? selectedClass.getQualifiedName() : "");

    myProject = manager.getProject();
    myChooserTitle = "Choose Class";
    addActionListener(new ChooseClassAction());
  }

  public String getChooserTitle() {
    return myChooserTitle;
  }

  public void setChooserTitle(final String chooserTitle) {
    myChooserTitle = chooserTitle;
  }

  private class ChooseClassAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      TreeClassChooser chooser = TreeClassChooserFactory.getInstance(myProject).createWithInnerClassesScopeChooser(myChooserTitle,
                                                                                                                   GlobalSearchScope.projectScope(myProject),
                                                                                                                   new TreeClassChooser.ClassFilter() {
        public boolean isAccepted(PsiClass aClass) {
          return aClass.getParent() instanceof PsiJavaFile || aClass.hasModifierProperty(PsiModifier.STATIC);
        }
      }, null);
      if (mySelectedClass != null) {
        chooser.selectDirectory(mySelectedClass.getContainingFile().getContainingDirectory());
      }
      chooser.showDialog();
      mySelectedClass = chooser.getSelectedClass();
      if (mySelectedClass != null) {
        setText(mySelectedClass.getQualifiedName());
      }
    }
  }
}