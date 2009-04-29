package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.actions.RenameElementAction;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.spellchecker.DictionarySuggestionProvider;
import com.intellij.spellchecker.inspections.SpellCheckerQuickFix;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public class RenameToQuickFix implements SpellCheckerQuickFix {

    public RenameToQuickFix() {
    }

    @NotNull
    public String getName() {
        return SpellCheckerBundle.message("rename.to");
    }




    @NotNull
    public String getFamilyName() {
        return SpellCheckerBundle.message("rename.to");
    }



    @Nullable
    private DictionarySuggestionProvider findProvider() {
        Object[] extensions = Extensions.getExtensions(NameSuggestionProvider.EP_NAME);
        DictionarySuggestionProvider provider = null;
        if (extensions != null) {
            for (Object extension : extensions) {
                if (extension instanceof DictionarySuggestionProvider) {
                    return (DictionarySuggestionProvider) extension;
                }
            }
        }
        return null;
    }

    
    @NotNull
    public Anchor getPopupActionAnchor() {
        return Anchor.FIRST;
    }

    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                DictionarySuggestionProvider provider = findProvider();
                if (provider != null) {
                    provider.setActive(true);
                }
                DataContext dataContext = DataManager.getInstance().getDataContext();
                AnAction action = new RenameElementAction();
                AnActionEvent aevent = new AnActionEvent(null, dataContext, "", action.getTemplatePresentation(), ActionManager.getInstance(), 0);
                action.actionPerformed(aevent);
                if (provider != null) {
                    provider.setActive(false);
                }
            }
        });
    }

}