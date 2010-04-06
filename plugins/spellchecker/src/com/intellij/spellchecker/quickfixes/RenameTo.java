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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.actions.RenameElementAction;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public class RenameTo extends ShowSuggestions implements SpellCheckerQuickFix {

  public RenameTo() {
    super();
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

    if (extensions != null) {
      for (Object extension : extensions) {
        if (extension instanceof DictionarySuggestionProvider) {
          return (DictionarySuggestionProvider)extension;
        }
      }
    }
    return null;
  }


  @NotNull
  public Anchor getPopupActionAnchor() {
    return Anchor.FIRST;
  }

  @SuppressWarnings({"SSBasedInspection"})
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        DictionarySuggestionProvider provider = findProvider();
        if (provider != null) {
          provider.setActive(true);
        }
        DataContext dataContext = DataManager.getInstance().getDataContext();
        AnAction action = new RenameElementAction();
        AnActionEvent event = new AnActionEvent(null, dataContext, "", action.getTemplatePresentation(), ActionManager.getInstance(), 0);
        action.actionPerformed(event);
        if (provider != null) {
          provider.setActive(false);
        }
      }
    });
  }

}