package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.ide.util.MemberChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;

/**
 *
 */
abstract class GenerateMembersHandlerBase implements CodeInsightActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.generation.GenerateMembersHandlerBase");

  private String myChooserTitle;
  protected boolean myToCopyJavaDoc = false;

  public GenerateMembersHandlerBase(String chooserTitle) {
    myChooserTitle = chooserTitle;
  }

  public final void invoke(final Project project, final Editor editor, PsiFile file) {
    Document document = editor.getDocument();
    if (!file.isWritable()){
      document.fireReadOnlyModificationAttempt();
      return;
    }
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    do {
      element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    } while (element instanceof PsiTypeParameter);

    final PsiClass aClass = (PsiClass) element;
    if (aClass == null || aClass.isInterface()) return; //?
    LOG.assertTrue(aClass.isValid());
    LOG.assertTrue(aClass.getContainingFile() != null);
    LOG.assertTrue(aClass.getContainingFile() == file);

    final Object[] members = chooseOriginalMembers(aClass, project);
    if (members == null) return;

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        doGenerate(project, editor, aClass, members);
      }
    });
  }

  private void doGenerate(final Project project, final Editor editor, PsiClass aClass, Object[] members) {
    int offset = editor.getCaretModel().getOffset();

    int col = editor.getCaretModel().getLogicalPosition().column;
    int line = editor.getCaretModel().getLogicalPosition().line;
    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(0, 0));

    Object[] newMembers = null;
    try{
      Object[] prototypes = generateMemberPrototypes(aClass, members);
      newMembers = GenerateMembersUtil.insertMembersAtOffset(project, editor.getDocument(), aClass.getContainingFile(), offset, prototypes);
    }
    catch(IncorrectOperationException e){
      LOG.error(e);
      return;
    }

    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(line, col));

    final ArrayList templates = new ArrayList();
    for(int i = 0; i < newMembers.length; i++){
      Object member = newMembers[i];
      if (member instanceof TemplateGenerationInfo){
        templates.add(member);
      }
    }

    if (!templates.isEmpty()){
      new ProcessTemplatesRunnable(project, templates, editor).run();
    }
    else if (newMembers.length > 0){
      positionCaret(editor, (PsiElement)newMembers[0]);
    }
  }

  protected Object[] chooseOriginalMembers(PsiClass aClass, Project project) {
    Object[] allMembers = getAllOriginalMembers(aClass);
    return chooseMembers(allMembers, false, false, project);
  }

  protected final Object[] chooseMembers(Object[] members, boolean allowEmptySelection, boolean copyJavadocCheckbox, Project project) {
    MemberChooser chooser = new MemberChooser(members, allowEmptySelection, true, project);
    chooser.setTitle(myChooserTitle);
    chooser.setCopyJavadocVisible(copyJavadocCheckbox);
    chooser.show();
    myToCopyJavaDoc = chooser.isCopyJavadoc();
    return chooser.getSelectedElements();
  }

  protected Object[] generateMemberPrototypes(PsiClass aClass, Object[] members) throws IncorrectOperationException {
    ArrayList array = new ArrayList();
    for(int i = 0; i < members.length; i++){
      Object[] prototypes = generateMemberPrototypes(aClass, members[i]);
      if (prototypes != null){
        for(int j = 0; j < prototypes.length; j++){
          array.add(prototypes[j]);
        }
      }
    }
    return array.toArray(new Object[array.size()]);
  }

  protected abstract Object[] getAllOriginalMembers(PsiClass aClass);

  protected abstract Object[] generateMemberPrototypes(PsiClass aClass, Object originalMember) throws IncorrectOperationException;

  protected void positionCaret(Editor editor, PsiElement firstMember) {
    GenerateMembersUtil.positionCaret(editor, firstMember, false);
  }

  public boolean startInWriteAction() {
    return false;
  }
}