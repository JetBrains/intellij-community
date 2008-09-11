package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GenerateTestMethodAction extends BaseGenerateAction {
  public GenerateTestMethodAction() {
    super(new MyHandler());
  }

  @Override
  protected PsiClass getTargetClass(Editor editor, PsiFile file) {
    return findTargetClass(editor, file);
  }

  private static PsiClass findTargetClass(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    return TestIntergationUtils.findOuterClass(element);
  }

  @Override
  protected boolean isValidForClass(PsiClass targetClass) {
    return TestIntergationUtils.isTest(targetClass) && findDescriptor(targetClass) != null;
  }

  private static TestFrameworkDescriptor findDescriptor(PsiClass targetClass) {
    for (TestFrameworkDescriptor each : Extensions.getExtensions(TestFrameworkDescriptor.EXTENSION_NAME)) {
      if (each.isTestClass(targetClass)) {
        return each;
      }
    }
    return null;
  }

  private static class MyHandler implements CodeInsightActionHandler {
    public void invoke(Project project, Editor editor, PsiFile file) {
      try {
        PsiMethod method = generateTestMethod(editor, file);
        runMethodTemplate(project, editor, method);
      }
      catch (IncorrectOperationException e) {
        throw new RuntimeException(e);
      }
    }

    private PsiMethod generateTestMethod(Editor editor, PsiFile file) throws IncorrectOperationException {
      PsiClass targetClass = findTargetClass(editor, file);
      List<GenerationInfo> members = new ArrayList<GenerationInfo>();

      TestFrameworkDescriptor d = findDescriptor(targetClass);

      final PsiMethod method = TestIntergationUtils.createMethod(targetClass, "test", d.getTestAnnotation());
      final PsiMethod[] result = new PsiMethod[1];

      members.add(new GenerationInfo() {
        @NotNull
        public PsiMember getPsiMember() {
          return method;
        }

        public void insert(PsiClass aClass, PsiElement anchor, boolean before) throws IncorrectOperationException {
          result[0] = (PsiMethod)GenerateMembersUtil.insert(aClass, method, anchor, before);
        }
      });

      int offset = findOffetToInsertMethodTo(editor, file);
      GenerateMembersUtil.insertMembersAtOffset(file, offset, members);

      return CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(result[0]);
    }

    private int findOffetToInsertMethodTo(Editor editor, PsiFile file) {
      int result = editor.getCaretModel().getOffset();

      PsiClass classAtCursor = PsiTreeUtil.getParentOfType(file.findElementAt(result), PsiClass.class, false);

      while(classAtCursor != null && !(classAtCursor.getParent() instanceof PsiFile)) {
        result = classAtCursor.getTextRange().getEndOffset();
        classAtCursor = PsiTreeUtil.getParentOfType(classAtCursor, PsiClass.class);
      }

      return result;
    }

    private void runMethodTemplate(Project project, final Editor editor, final PsiMethod method) {
      Template template = TemplateManager.getInstance(project).createTemplate("", "");

      ConstantNode name = new ConstantNode("Name");
      template.addVariable("", name, name, true);

      editor.getCaretModel().moveToOffset(method.getNameIdentifier().getTextRange().getEndOffset());

      TemplateManager.getInstance(project).startTemplate(editor, template, new TemplateEditingAdapter() {
        @Override
        public void templateFinished(Template template) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              selectMethodContent(editor, method);
            }
          });
        }
      });
    }

    private void selectMethodContent(Editor editor, PsiMethod method) {
      PsiElement bodyElementToSelect = method.getBody().getFirstBodyElement();
      while (bodyElementToSelect != null && !(bodyElementToSelect instanceof PsiComment)) {
        bodyElementToSelect = bodyElementToSelect.getNextSibling();
      }
      TextRange range;
      if (bodyElementToSelect != null) {
        range = bodyElementToSelect.getTextRange();
      }
      else {
        range = new TextRange(method.getBody().getLBrace().getTextRange().getEndOffset(),
                              method.getBody().getRBrace().getTextRange().getStartOffset());
      }
      editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      editor.getCaretModel().moveToOffset(range.getEndOffset());
    }

    public boolean startInWriteAction() {
      return true;
    }
  }
}
