// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.actions;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.binding.FormClassIndex;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;


public class GenerateMainAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(GenerateMainAction.class);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;
    final Editor editor = e.getData(CommonDataKeys.EDITOR);
    assert editor != null;
    final int offset = editor.getCaretModel().getOffset();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    PsiClass psiClass = PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiClass.class);
    assert psiClass != null;

    if (!PsiUtil.hasDefaultConstructor(psiClass)) {
      Messages.showMessageDialog(project, UIDesignerBundle.message("generate.main.no.default.constructor"), UIDesignerBundle.message("generate.main.title"), Messages.getErrorIcon());
      return;
    }

    final List<PsiFile> boundForms = FormClassIndex.findFormsBoundToClass(project, psiClass.getQualifiedName());
    final LwRootContainer rootContainer;
    try {
      rootContainer = Utils.getRootContainer(boundForms.get(0).getText(), null);
    }
    catch (AlienFormFileException ex) {
      Messages.showMessageDialog(project, UIDesignerBundle.message("generate.main.not.valid.form"),
                                 UIDesignerBundle.message("generate.main.title"), Messages.getErrorIcon());
      return;
    }
    catch (Exception ex) {
      LOG.error(ex);
      return;
    }

    if (rootContainer.getComponentCount() == 0) {
      Messages.showMessageDialog(project, UIDesignerBundle.message("generate.main.empty.form"),
                                 UIDesignerBundle.message("generate.main.title"), Messages.getErrorIcon());
      return;
    }
    String rootBinding = rootContainer.getComponent(0).getBinding();
    if (rootBinding == null || psiClass.findFieldByName(rootBinding, true) == null) {
      Messages.showMessageDialog(project, UIDesignerBundle.message("generate.main.no.root.binding"),
                                 UIDesignerBundle.message("generate.main.title"), Messages.getErrorIcon());
      return;
    }

    @NonNls final StringBuilder mainBuilder = new StringBuilder("public static void main(String[] args) { ");
    final JavaCodeStyleManager csm = JavaCodeStyleManager.getInstance(project);
    SuggestedNameInfo nameInfo = csm.suggestVariableName(VariableKind.LOCAL_VARIABLE, "frame", null, null);
    String varName = nameInfo.names [0];
    mainBuilder.append(JFrame.class.getName()).append(" ").append(varName).append("= new ").append(JFrame.class.getName());
    mainBuilder.append("(\"").append(psiClass.getName()).append("\");");
    mainBuilder.append(varName).append(".setContentPane(new ").append(psiClass.getQualifiedName()).append("().").append(rootBinding).append(");");
    mainBuilder.append(varName).append(".setDefaultCloseOperation(").append(JFrame.class.getName()).append(".EXIT_ON_CLOSE);");
    mainBuilder.append(varName).append(".pack();");
    mainBuilder.append(varName).append(".setVisible(true);");

    mainBuilder.append("}\n");

    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        PsiMethod method =
          JavaPsiFacade.getInstance(file.getProject()).getElementFactory().createMethodFromText(mainBuilder.toString(), file);
        List<PsiGenerationInfo<PsiMethod>> infos = Collections.singletonList(new PsiGenerationInfo<>(method));
        List<PsiGenerationInfo<PsiMethod>> resultMembers = GenerateMembersUtil.insertMembersAtOffset(file, offset, infos);
        resultMembers.get(0).positionCaret(editor, false);
      }
      catch (IncorrectOperationException e1) {
        LOG.error(e1);
      }
    }), null, null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enabled = isActionEnabled(e);
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(enabled);
  }

  private static boolean isActionEnabled(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return false;
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return false;
    int offset = editor.getCaretModel().getOffset();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return false;
    PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass == null) return false;
    if (PsiMethodUtil.findMainMethod(psiClass) != null) return false;
    if (FormClassIndex.findFormsBoundToClass(project, psiClass).isEmpty()) return false;
    return true;
  }
}
