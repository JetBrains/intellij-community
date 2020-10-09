package de.plushnikov.intellij.plugin.action;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

import java.util.List;

/**
 * Date: 15.12.13 Time: 23:06
 */
public abstract class BaseRefactorHandler implements Runnable {
  protected final Project project;
  protected final Editor editor;
  private final MemberChooser<ClassMember> chooser;

  public BaseRefactorHandler(DataContext dataContext, Project project) {
    this.project = project;
    editor = PlatformDataKeys.EDITOR.getData(dataContext);

    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    PsiClass psiClass = OverrideImplementUtil.getContextClass(project, editor, psiFile, false);

    List<EncapsulatableClassMember> classMembers = getEncapsulatableClassMembers(psiClass);
    chooser = new MemberChooser<>(
      classMembers.toArray(new ClassMember[0]), true, true, project);
    chooser.setTitle(getChooserTitle());
    chooser.setCopyJavadocVisible(false);
  }

  public boolean processChooser() {
    chooser.show();

    List<ClassMember> selectedElements = chooser.getSelectedElements();

    if (selectedElements == null) {
      HintManager.getInstance().showErrorHint(editor, getNothingFoundMessage());
      return false;
    }
    if (selectedElements.isEmpty()) {
      HintManager.getInstance().showErrorHint(editor, getNothingAcceptedMessage());
      return false;
    }
    return true;
  }

  protected abstract String getChooserTitle();

  protected abstract String getNothingFoundMessage();

  protected abstract String getNothingAcceptedMessage();

  protected abstract List<EncapsulatableClassMember> getEncapsulatableClassMembers(PsiClass psiClass);

  @Override
  public void run() {
    if (!EditorModificationUtil.checkModificationAllowed(editor)) {
      return;
    }
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
      return;
    }

    process(chooser.getSelectedElements());
  }

  protected abstract void process(List<ClassMember> classMembers);

}
