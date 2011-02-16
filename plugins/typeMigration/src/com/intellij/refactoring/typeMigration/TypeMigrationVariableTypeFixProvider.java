/*
 * User: anna
 * Date: 27-Aug-2009
 */
package com.intellij.refactoring.typeMigration;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableTypeFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.ChangeVariableTypeQuickFixProvider;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypeMigrationVariableTypeFixProvider implements ChangeVariableTypeQuickFixProvider {
  private static final Logger LOG1 = Logger.getInstance("#" + TypeMigrationVariableTypeFixProvider.class.getName());

  public IntentionAction[] getFixes(PsiVariable variable, PsiType toReturn) {
    return new IntentionAction[]{new VariableTypeFix(variable, toReturn) {
      @NotNull
      @Override
      public String getName() {
        return "Migrate \'" + getVariable().getName() + "\' type to \'" + getReturnType().getCanonicalText() + "\'";
      }

      @Override
      public void applyFix(Project project, PsiFile file, @Nullable Editor editor) {
        if (!CodeInsightUtilBase.prepareFileForWrite(getVariable().getContainingFile())) return;
        try {
          getVariable().normalizeDeclaration();
          final TypeMigrationRules rules = new TypeMigrationRules(TypeMigrationLabeler.getElementType(getVariable()));
          rules.setMigrationRootType(getReturnType());
          rules.setBoundScope(GlobalSearchScope.projectScope(project));
          TypeMigrationProcessor.runHighlightingTypeMigration(project, editor, rules, getVariable());
          JavaCodeStyleManager.getInstance(project).shortenClassReferences(getVariable());
          UndoUtil.markPsiFileForUndo(file);
        }
        catch (IncorrectOperationException e) {
          LOG1.error(e);
        }
      }
    }};
  }
}
