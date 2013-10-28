package org.jetbrains.postfixCompletion;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.psi.PsiFile;

public class TestActionFoo extends AnAction {
    public void actionPerformed(AnActionEvent e) {
        PsiFile file =  e.getData(LangDataKeys.PSI_FILE);

        // TODO: insert action logic here
    }

    @Override
    public void update(AnActionEvent e) {
        PsiFile file =  e.getData(LangDataKeys.PSI_FILE);

        e.getPresentation().setEnabled(file != null);


        //file.getFirstChild().delete();
    }
}
