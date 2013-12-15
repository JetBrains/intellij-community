package de.plushnikov.intellij.plugin.action;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiElementClassMember;
import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PropertyUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Date: 15.12.13 Time: 20:28
 */
public class RefactorGetterHandler implements Runnable {
  private final Project project;
  private final Editor editor;

  private final MemberChooser<ClassMember> chooser;

  public RefactorGetterHandler(Project project, DataContext dataContext) {
    this.project = project;
    editor = CommonDataKeys.EDITOR.getData(dataContext);

    PsiFile psiFile = DataKeys.PSI_FILE.getData(dataContext);
    PsiClass psiClass = OverrideImplementUtil.getContextClass(project, editor, psiFile, false);

    List<EncapsulatableClassMember> classMembers = getEncapsulatableClassMembers(psiClass);
    chooser = new MemberChooser<ClassMember>(
       classMembers.toArray(new ClassMember[classMembers.size()]), true, true, project);
    chooser.setTitle("Select Fields to Replace Getter-Method With @Getter");
    chooser.setCopyJavadocVisible(true);
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

  protected String getNothingFoundMessage() {
    return "No field getter have been found to generate @Getters for";
  }

  protected String getNothingAcceptedMessage() {
    return "No fields with getter method were found";
  }

  protected List<EncapsulatableClassMember> getEncapsulatableClassMembers(PsiClass psiClass) {
    final List<EncapsulatableClassMember> result = new ArrayList<EncapsulatableClassMember>();
    for (PsiField field : psiClass.getFields()) {
      if (null != PropertyUtil.findGetterForField(field)) {
        result.add(new PsiFieldMember(field));
      }
    }
    return result;
  }

  @Override
  public void run() {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
      return;
    }

    process(chooser.getSelectedElements());
  }

  private void process(List<ClassMember> classMembers) {
    for (ClassMember classMember : classMembers) {
      final PsiElementClassMember elementClassMember = (PsiElementClassMember) classMember;

      PsiField psiField = (PsiField) elementClassMember.getPsiElement();
      PsiMethod getterForField = PropertyUtil.findGetterForField(psiField);
      if (null != getterForField) {
        PsiModifierList modifierList = psiField.getModifierList();
        if (null != modifierList) {
          modifierList.addAnnotation("lombok.Getter");
          getterForField.delete();
        }
      }
    }
  }
}
