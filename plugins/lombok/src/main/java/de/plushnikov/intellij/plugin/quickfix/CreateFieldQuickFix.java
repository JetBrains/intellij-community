package de.plushnikov.intellij.plugin.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Plushnikov Michail
 */
public class CreateFieldQuickFix extends LocalQuickFixOnPsiElement implements IntentionAction {
  private final String myName;
  private final PsiType myType;
  private final String myInitializerText;
  private final Collection<String> myModifiers;

  public CreateFieldQuickFix(@NotNull PsiClass psiClass, @NotNull String name, @NotNull PsiType psiType, @Nullable String initializerText, String... modifiers) {
    super(psiClass);
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
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return isAvailable();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    applyFix();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile psiFile, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    final PsiClass myClass = (PsiClass) startElement;
    final Editor editor = CodeInsightUtil.positionCursor(project, psiFile, myClass.getLBrace());
    if (editor != null) {
      WriteCommandAction.writeCommandAction(project, psiFile).run(() ->
        {
          final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(project);
          final PsiField psiField = psiElementFactory.createField(myName, myType);

          final PsiModifierList modifierList = psiField.getModifierList();
          if (null != modifierList) {
            for (String modifier : myModifiers) {
              modifierList.setModifierProperty(modifier, true);
            }
          }
          if (null != myInitializerText) {
            PsiExpression psiInitializer = psiElementFactory.createExpressionFromText(myInitializerText, psiField);
            psiField.setInitializer(psiInitializer);
          }

          final List<PsiGenerationInfo<PsiField>> generationInfos = GenerateMembersUtil.insertMembersAtOffset(myClass.getContainingFile(), editor.getCaretModel().getOffset(),
            Collections.singletonList(new PsiGenerationInfo<>(psiField)));
          if (!generationInfos.isEmpty()) {
            PsiField psiMember = generationInfos.iterator().next().getPsiMember();
            editor.getCaretModel().moveToOffset(psiMember.getTextRange().getEndOffset());
          }

          UndoUtil.markPsiFileForUndo(psiFile);
        }
      );
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

}
