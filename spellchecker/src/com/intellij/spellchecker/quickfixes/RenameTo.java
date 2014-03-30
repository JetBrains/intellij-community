/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spellchecker.quickfixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.refactoring.actions.RenameElementAction;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.RenameHandlerRegistry;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;


public class RenameTo extends ShowSuggestions implements SpellCheckerQuickFix {

  public RenameTo(String wordWithTypo) {
    super(wordWithTypo);
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
  private static DictionarySuggestionProvider findProvider() {
    Object[] extensions = Extensions.getExtensions(NameSuggestionProvider.EP_NAME);

    for (Object extension : extensions) {
      if (extension instanceof DictionarySuggestionProvider) {
        return (DictionarySuggestionProvider)extension;
      }
    }
    return null;
  }


  @NotNull
  public Anchor getPopupActionAnchor() {
    return Anchor.FIRST;
  }

  @SuppressWarnings({"SSBasedInspection"})
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    Runnable fix = new Runnable() {
      public void run() {
        DictionarySuggestionProvider provider = findProvider();
        if (provider != null) {
          provider.setActive(true);
        }

        Editor editor = getEditorFromFocus();
        HashMap<String, Object> map = new HashMap<String, Object>();
        PsiElement psiElement = descriptor.getPsiElement();
        if (psiElement == null) return;
        PsiFile containingFile = psiElement.getContainingFile();
        if (editor == null) {
          editor = InjectedLanguageUtil.openEditorFor(containingFile, project);
        }

        if (editor == null) return;

        if (editor instanceof EditorWindow) {
          map.put(CommonDataKeys.EDITOR.getName(), editor);
          map.put(CommonDataKeys.PSI_ELEMENT.getName(), psiElement);
        } else if (ApplicationManager.getApplication().isUnitTestMode()) { // TextEditorComponent / FiledEditorManagerImpl give away the data in real life
          map.put(
            CommonDataKeys.PSI_ELEMENT.getName(),
            new TextEditorPsiDataProvider().getData(
              CommonDataKeys.PSI_ELEMENT.getName(),
              editor, 
              containingFile.getVirtualFile()
            )
          );
        }

        final Boolean selectAll = editor.getUserData(RenameHandlerRegistry.SELECT_ALL);
        try {
          editor.putUserData(RenameHandlerRegistry.SELECT_ALL, true);
          DataContext dataContext = SimpleDataContext.getSimpleContext(map, DataManager.getInstance().getDataContext(editor.getComponent()));
          AnAction action = new RenameElementAction();
          AnActionEvent event = new AnActionEvent(null, dataContext, "", action.getTemplatePresentation(), ActionManager.getInstance(), 0);
          action.actionPerformed(event);
          if (provider != null) {
            provider.setActive(false);
          }
        }
        finally {
          editor.putUserData(RenameHandlerRegistry.SELECT_ALL, selectAll);
        }
      }
    };
    
    if (ApplicationManager.getApplication().isUnitTestMode()) fix.run();
    else SwingUtilities.invokeLater(fix); // TODO [shkate] this is hard to test!
  }

  @Nullable
  private static Editor getEditorFromFocus() {
    final Component c = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (c instanceof EditorComponentImpl) {
      return ((EditorComponentImpl)c).getEditor();
    }
    return null;
  }
}