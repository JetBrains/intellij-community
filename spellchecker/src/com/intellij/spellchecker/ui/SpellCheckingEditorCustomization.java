/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.ui.SimpleEditorCustomization;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Allows to enforce editors to use/don't use spell checking ignoring user-defined spelling inspection settings.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since Aug 20, 2010 3:54:42 PM
 */
public class SpellCheckingEditorCustomization extends SimpleEditorCustomization {
  /**
   * @deprecated use {@link SpellCheckingEditorCustomizationProvider#getEnabledCustomization()} instead
   */
  public static final SpellCheckingEditorCustomization ENABLED = (SpellCheckingEditorCustomization)SpellCheckingEditorCustomizationProvider.getInstance().getEnabledCustomization();

  /**
   * @deprecated use {@link SpellCheckingEditorCustomizationProvider#getDisabledCustomization()} instead
   */
  public static final SpellCheckingEditorCustomization DISABLED = (SpellCheckingEditorCustomization)SpellCheckingEditorCustomizationProvider.getInstance().getDisabledCustomization();

  private static final Map<String, LocalInspectionToolWrapper> SPELL_CHECK_TOOLS = new HashMap<String, LocalInspectionToolWrapper>();
  private static final boolean READY = init();

  @NotNull
  public static SpellCheckingEditorCustomization getInstance(boolean enabled) {
    return (SpellCheckingEditorCustomization)SpellCheckingEditorCustomizationProvider.getInstance().getCustomization(enabled);
  }

  SpellCheckingEditorCustomization(boolean enabled) {
    super(enabled);
  }

  @SuppressWarnings({"unchecked"})
  private static boolean init() {
    // It's assumed that default spell checking inspection settings are just fine for processing all types of data.
    // Please perform corresponding settings tuning if that assumption is broken at future.

    Class<LocalInspectionTool>[] inspectionClasses = (Class<LocalInspectionTool>[])new Class<?>[] {SpellCheckingInspection.class};
    for (Class<LocalInspectionTool> inspectionClass : inspectionClasses) {
      try {
        LocalInspectionTool tool = inspectionClass.newInstance();
        SPELL_CHECK_TOOLS.put(tool.getShortName(), new LocalInspectionToolWrapper(tool));
      }
      catch (Throwable e) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void customize(@NotNull EditorEx editor) {
    boolean apply = isEnabled();

    if (!READY) {
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

    NotNullFunction<InspectionProfileWrapper, InspectionProfileWrapper> strategy = file.getUserData(InspectionProfileWrapper.CUSTOMIZATION_KEY);
    if (strategy == null) {
      file.putUserData(InspectionProfileWrapper.CUSTOMIZATION_KEY, strategy = new MyInspectionProfileStrategy());
    }

    if (!(strategy instanceof MyInspectionProfileStrategy)) {
      return;
    }

    ((MyInspectionProfileStrategy)strategy).setUseSpellCheck(apply);

    if (apply) {
      editor.putUserData(IntentionManager.SHOW_INTENTION_OPTIONS_KEY, false);
    }

    // Update representation.
    DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(project);
    if (analyzer != null) {
      analyzer.restart(file);
    }
  }

  private static class MyInspectionProfileStrategy implements NotNullFunction<InspectionProfileWrapper, InspectionProfileWrapper> {

    private final Map<InspectionProfileWrapper, MyInspectionProfileWrapper> myWrappers
      = new WeakHashMap<InspectionProfileWrapper, MyInspectionProfileWrapper>();
    private boolean myUseSpellCheck;

    @NotNull
    @Override
    public InspectionProfileWrapper fun(InspectionProfileWrapper inspectionProfileWrapper) {
      if (!READY) {
        return inspectionProfileWrapper;
      }
      MyInspectionProfileWrapper wrapper = myWrappers.get(inspectionProfileWrapper);
      if (wrapper == null) {
        myWrappers.put(inspectionProfileWrapper, wrapper = new MyInspectionProfileWrapper(inspectionProfileWrapper));
      }
      wrapper.setUseSpellCheck(myUseSpellCheck);
      return wrapper;
    }

    public void setUseSpellCheck(boolean useSpellCheck) {
      myUseSpellCheck = useSpellCheck;
    }
  }

  private static class MyInspectionProfileWrapper extends InspectionProfileWrapper {
    private final InspectionProfileWrapper myDelegate;
    private boolean myUseSpellCheck;

    MyInspectionProfileWrapper(InspectionProfileWrapper delegate) {
      super(new InspectionProfileImpl("CommitDialog"));
      myDelegate = delegate;
    }

    @Override
    public boolean isToolEnabled(HighlightDisplayKey key, PsiElement element) {
      return SPELL_CHECK_TOOLS.containsKey(key.toString()) && myUseSpellCheck;
    }

    public void setUseSpellCheck(boolean useSpellCheck) {
      myUseSpellCheck = useSpellCheck;
    }
  }
}
