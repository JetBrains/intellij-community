package de.plushnikov.intellij.plugin.extension.postfix;

import com.intellij.codeInsight.template.postfix.templates.PostfixIntroduceSite;
import com.intellij.codeInsight.template.postfix.templates.PostfixModExpander;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.refactoring.introduceVariable.JavaIntroduceVariableModCommandService;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class LombokVarValPostfixTemplate extends PostfixTemplateWithExpressionSelector {

  private static final PostfixTemplateExpressionSelector MY_SELECTOR =
    JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset(JavaPostfixTemplatesUtils.IS_NON_VOID);

  private final String selectedTypeFQN;

  LombokVarValPostfixTemplate(String name, String example, String selectedTypeFQN) {
    super(null, name, example, MY_SELECTOR, null);
    this.selectedTypeFQN = selectedTypeFQN;
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    // Non-inplace mode would require a modal dialog, which is not allowed under postfix templates
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    if (editorSettings != null && !editorSettings.isVariableInplaceRenameEnabled()) return false;
    if (!super.isApplicable(context, copyDocument, newOffset)) return false;
    if (JavaPostfixTemplatesUtils.isInExpressionFile(context)) return false;
    if (JavaIntroduceVariableModCommandService.getInstance() == null) return false;
    Module module = ModuleUtilCore.findModuleForPsiElement(context);
    return LombokLibraryUtil.hasLombokClasses(module);
  }

  @Override
  protected void expandForChooseExpression(@NotNull PsiElement expression, @NotNull Editor editor) {
    IntroduceVariableHandler handler = new IntroduceLombokVariableHandler(selectedTypeFQN);
    handler.invoke(expression.getProject(), editor, (PsiExpression)expression);
  }

  @Override
  public boolean isApplicableForModCommand() {
    return true;
  }

  @Override
  public @NotNull PostfixModExpander createModExpander() {
    return (ActionContext actionContext, PostfixTemplateProvider provider, TextRange keyRange) -> {
      List<PsiExpression> candidates = PostfixIntroduceSite.selectExpressions(actionContext, provider, keyRange, MY_SELECTOR);
      return PostfixIntroduceSite.chooseExpression(actionContext, candidates, MY_SELECTOR,
                                                   (ctx, expr) -> introduceVariableCommand(ctx, expr, provider, keyRange));
    };
  }

  /**
   * Introduces a {@code lombok.val} or a {@code lombok.var} variable for {@code virtualExpr}, an expression of the copy
   * of the file this template analysed.
   */
  private @NotNull ModCommand introduceVariableCommand(@NotNull ActionContext ctx,
                                                       @NotNull PsiExpression virtualExpr,
                                                       @NotNull PostfixTemplateProvider provider,
                                                       @NotNull TextRange keyRange) {
    JavaIntroduceVariableModCommandService service = JavaIntroduceVariableModCommandService.getInstance();
    if (service == null) return ModCommand.nop();
    //noinspection HardCodedStringLiteral
    String familyName = MY_SELECTOR.getRenderer().fun(virtualExpr);
    return service.introduceVariableCommand(ctx, new PostfixIntroduceSite(virtualExpr, provider, keyRange),
                                            service.getContext(virtualExpr), familyName, selectedTypeFQN);
  }
}
