package de.plushnikov.intellij.plugin.quickfix;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import de.plushnikov.intellij.plugin.LombokBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * @author Plushnikov Michail
 */
public class CreateFieldQuickFix extends PsiUpdateModCommandAction<PsiClass> {
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

  @Override
  @NotNull
  public String getFamilyName() {
    return LombokBundle.message("intention.name.create.new.field.s", myName);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiClass myClass, @NotNull ModPsiUpdater updater) {
    PsiElement lBrace = myClass.getLBrace();
    if (lBrace == null) return;

    final PsiElementFactory psiElementFactory = JavaPsiFacade.getElementFactory(context.project());
    final PsiField psiField = psiElementFactory.createField(myName, myType);

    final PsiModifierList modifierList = psiField.getModifierList();
    if (modifierList != null) {
      for (String modifier : myModifiers) {
        modifierList.setModifierProperty(modifier, true);
      }
    }
    if (myInitializerText != null) {
      PsiExpression psiInitializer = psiElementFactory.createExpressionFromText(myInitializerText, psiField);
      psiField.setInitializer(psiInitializer);
    }

    final List<PsiGenerationInfo<PsiField>> generationInfos = GenerateMembersUtil.insertMembersAtOffset(
      myClass.getContainingFile(), lBrace.getTextRange().getEndOffset(),
      List.of(new PsiGenerationInfo<>(psiField)));
    if (!generationInfos.isEmpty()) {
      PsiField psiMember = Objects.requireNonNull(generationInfos.iterator().next().getPsiMember());
      updater.moveCaretTo(psiMember.getTextRange().getEndOffset());
    }
  }
}
