/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.spellchecker.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.inspections.SpellCheckerInspectionToolProvider;
import com.intellij.ui.EditorCustomization;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Makes current editor to have spell checking turned on all the time.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since Aug 20, 2010 3:54:42 PM
 */
public class SpellCheckingEditorCustomization implements EditorCustomization {

  /**
   * Holds custom inspection profile wrapper.
   * <p/>
   * The general idea is that we want to use existing spell checking functionality within target editor all the time.
   * Unfortunately, we can't do that as-is because spell checking inspection may be disabled or specifically configured
   * (e.g. it fails to work with a 'plain text' if 'process code' option is not set).
   * <p/>
   * Hence, we define custom profile that is used during target editor highlighting.
   */
  @Nullable
  private static final InspectionProfileWrapper INSPECTION_PROFILE_WRAPPER = initProvider();

  @SuppressWarnings("unchecked")
  @Nullable
  private static InspectionProfileWrapper initProvider() {
    // It's assumed that default spell checking inspection settings are just fine for processing all types of data.
    // Please perform corresponding settings tuning if that assumption is broken at future.
    InspectionToolProvider provider = new SpellCheckerInspectionToolProvider();

    final Map<String, LocalInspectionTool> tools = new HashMap<String, LocalInspectionTool>();
    Class<LocalInspectionTool>[] inspectionClasses = (Class<LocalInspectionTool>[])provider.getInspectionClasses();
    for (Class<LocalInspectionTool> inspectionClass : inspectionClasses) {
      try {
        LocalInspectionTool tool = inspectionClass.newInstance();
        tools.put(tool.getShortName(), tool);
      }
      catch (Throwable e) {
        return null;
      }
    }

    InspectionProfile profile = new InspectionProfileImpl("CommitMessage") {

      private final LocalInspectionTool[] myToolsArray = tools.values().toArray(new LocalInspectionTool[tools.size()]);

      @Override
      public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey inspectionToolKey, PsiElement element) {
        return HighlightDisplayLevel.WARNING;
      }

      @Override
      public InspectionProfileEntry getInspectionTool(@NotNull String shortName, @NotNull PsiElement element) {
        return tools.get(shortName);
      }

      @NotNull
      @Override
      public InspectionProfileEntry[] getInspectionTools(PsiElement element) {
        return myToolsArray;
      }

      @Override
      public boolean isToolEnabled(HighlightDisplayKey key, PsiElement element) {
        return true;
      }
    };

    final List<LocalInspectionTool> toolsList = new ArrayList<LocalInspectionTool>(tools.values());

    return new InspectionProfileWrapper(profile) {
      @Override
      public List<LocalInspectionTool> getHighlightingLocalInspectionTools(PsiElement element) {
        return toolsList;
      }
    };
  }

  @Override
  public Set<Feature> getSupportedFeatures() {
    return EnumSet.of(Feature.SPELL_CHECK);
  }

  @Override
  public void customize(@NotNull EditorEx editor, @NotNull Feature feature) {
    if (INSPECTION_PROFILE_WRAPPER == null) {
      return;
    }

    Project project = editor.getProject();
    if (project == null) {
      return;
    }

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) {
      return;
    }
    file.putUserData(InspectionProfileWrapper.KEY, INSPECTION_PROFILE_WRAPPER);
    editor.putUserData(IntentionManager.SHOW_INTENTION_OPTIONS_KEY, false);
  }
}
