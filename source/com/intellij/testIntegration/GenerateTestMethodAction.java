package com.intellij.testIntegration;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
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
    return TestIntegrationUtils.findOuterClass(element);
  }

  @Override
  protected boolean isValidForClass(PsiClass targetClass) {
    return TestIntegrationUtils.isTest(targetClass) && findDescriptor(targetClass) != null;
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
        PsiClass targetClass = findTargetClass(editor, file);
        PsiMethod method = generateDummyMethod(editor, file, targetClass);
        runMethodTemplate(project, editor, file, targetClass, method);
      }
      catch (IncorrectOperationException e) {
        throw new RuntimeException(e);
      }
    }

    private PsiMethod generateDummyMethod(Editor editor, PsiFile file, PsiClass targetClass) throws IncorrectOperationException {
      List<GenerationInfo> members = new ArrayList<GenerationInfo>();

      PsiElementFactory f = JavaPsiFacade.getInstance(targetClass.getProject()).getElementFactory();
      final PsiMethod method = f.createMethod("dummy", PsiType.VOID);
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

      while (classAtCursor != null && !(classAtCursor.getParent() instanceof PsiFile)) {
        result = classAtCursor.getTextRange().getEndOffset();
        classAtCursor = PsiTreeUtil.getParentOfType(classAtCursor, PsiClass.class);
      }

      return result;
    }

    private void runMethodTemplate(final Project project, final Editor editor, PsiFile file, PsiClass targetClass, final PsiMethod method) {
      Template template = createMethodTemplate(project, targetClass);

      final TextRange range = method.getTextRange();
      editor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), "");
      editor.getCaretModel().moveToOffset(range.getStartOffset());

      TemplateManager.getInstance(project).startTemplate(editor, template, new TemplateEditingAdapter() {
        @Override
        public void templateFinished(Template template) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
              PsiFile psi = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
              PsiElement method = psi.findElementAt(range.getStartOffset());

              if (method != null) {
                method = PsiTreeUtil.getParentOfType(method, PsiMethod.class, false);
                if (method != null) {
                  CreateFromUsageUtils.setupEditor((PsiMethod)method, editor);
                }
              }
            }
          });
        }
      });
    }

    private Template createMethodTemplate(Project project, PsiClass targetClass) {
      TestFrameworkDescriptor descriptor = findDescriptor(targetClass);

      String templateName = FileUtil.getNameWithoutExtension(descriptor.getTestMethodFileTemplateDescriptor().getFileName());
      FileTemplate fileTemplate = FileTemplateManager.getInstance().getTemplate(templateName);
      Template template = TemplateManager.getInstance(project).createTemplate("", "");

      String templateText = fileTemplate.getText();
      int index = templateText.indexOf("${NAME}");

      if (index == -1) {
        template.addTextSegment(templateText);
      }
      else {
        String nameString = "Name";
        if (index > 0) {
          if (Character.isWhitespace(templateText.charAt(index - 1))) {
            nameString = nameString.toLowerCase();
          }
        }
        template.addTextSegment(templateText.substring(0, index));
        Expression name = new ConstantNode(nameString);
        template.addVariable("", name, name, true);
        template.addTextSegment(templateText.substring(index + "${NAME}".length(), templateText.length()));
      }

      return template;
    }

    public boolean startInWriteAction() {
      return true;
    }
  }
}
