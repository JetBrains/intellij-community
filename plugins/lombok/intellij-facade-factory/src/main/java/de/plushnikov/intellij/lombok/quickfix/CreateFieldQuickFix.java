package de.plushnikov.intellij.lombok.quickfix;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.Modifier;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;

/**
 * @author Plushnikov Michail
 */
public class CreateFieldQuickFix implements IntentionAction, LocalQuickFix {
  private final PsiClass           myClass;
  private final String             myName;
  private final PsiType            myType;
  private final String             myInitializerText;
  private final Collection<String> myModifiers;

  public CreateFieldQuickFix(@NotNull PsiClass psiClass, @NotNull String name, @NotNull PsiType psiType, @Nullable String initializerText, String... modifiers) {
    myClass = psiClass;
    myName = name;
    myType = psiType;
    myInitializerText = initializerText;
    myModifiers = Arrays.asList(modifiers);
  }

  @NotNull
  public String getText() {
    return String.format("Create new field '%s'", myName);
  }

  @NotNull
  public String getName() {
    return getText();
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    applyFixInner(project);
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    applyFixInner(project);
  }

  private void applyFixInner(final Project project) {
    final PsiFile psiFile = myClass.getContainingFile();
    final Editor editor = CodeInsightUtil.positionCursor(project, psiFile, myClass.getLBrace());
    if (editor != null) {
      new WriteCommandAction(project, psiFile) {
        protected void run(Result result) throws Throwable {

          final PsiElementFactory psiElementFactory = JavaPsiFacade.getInstance(myClass.getProject()).getElementFactory();
          final PsiField psiField = psiElementFactory.createField(myName, myType);

          final PsiModifierList modifierList = psiField.getModifierList();
          if (null != modifierList) {
            for (@Modifier String modifier : myModifiers) {
              modifierList.setModifierProperty(modifier, true);
            }
          }
          if (null != myInitializerText) {
            PsiExpression psiInitializer = psiElementFactory.createExpressionFromText(myInitializerText, psiField);
            psiField.setInitializer(psiInitializer);
          }

          final List<PsiGenerationInfo<PsiField>> generationInfos = GenerateMembersUtil.insertMembersAtOffset(myClass.getContainingFile(), editor.getCaretModel().getOffset(),
              Collections.singletonList(new PsiGenerationInfo<PsiField>(psiField)));
          if (!generationInfos.isEmpty()) {
            PsiField psiMember = generationInfos.iterator().next().getPsiMember();
            editor.getCaretModel().moveToOffset(psiMember.getTextRange().getEndOffset());
          }

          UndoUtil.markPsiFileForUndo(psiFile);
        }

        @Override
        protected boolean isGlobalUndoAction() {
          return true;
        }
      }.execute();
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

}
