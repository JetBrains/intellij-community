// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.ui.SimpleEditorCustomization;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Allows enforcing editors to use/not use spell checking, ignoring user-defined spelling inspection settings.
 * <p/>
 * Thread-safe.
 */
public class SpellCheckingEditorCustomization extends SimpleEditorCustomization {
  private static final Map<String, LocalInspectionToolWrapper> SPELL_CHECK_TOOLS = new HashMap<>();
  private static final boolean READY = init();

  SpellCheckingEditorCustomization(boolean enabled) {
    super(enabled);
  }

  @SuppressWarnings("unchecked")
  private static boolean init() {
    // It's assumed that default spell checking inspection settings are just fine for processing all types of data.
    // Please perform corresponding settings tuning if that assumption is broken in the future.

    Class<LocalInspectionTool>[] inspectionClasses = (Class<LocalInspectionTool>[])new Class<?>[]{SpellCheckingInspection.class};
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

    PsiFile file = ReadAction.compute(() -> PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()));
    if (file == null) {
      return;
    }

    Function<? super InspectionProfile, ? extends InspectionProfileWrapper> strategy = InspectionProfileWrapper.getCustomInspectionProfileWrapper(file);
    if (strategy == null) {
      strategy = new MyInspectionProfileStrategy();
      InspectionProfileWrapper.setCustomInspectionProfileWrapperTemporarily(file, strategy);
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

  public static boolean isSpellCheckingDisabled(@NotNull PsiFile file) {
    Function<? super InspectionProfile, ? extends InspectionProfileWrapper>
      strategy = InspectionProfileWrapper.getCustomInspectionProfileWrapper(file);
    return strategy instanceof MyInspectionProfileStrategy && !((MyInspectionProfileStrategy)strategy).myUseSpellCheck;
  }

  public static Set<String> getSpellCheckingToolNames() {
    return Collections.unmodifiableSet(SPELL_CHECK_TOOLS.keySet());
  }

  private static class MyInspectionProfileStrategy implements Function<InspectionProfile, InspectionProfileWrapper> {
    private final ConcurrentMap<InspectionProfile, MyInspectionProfileWrapper> myWrappers
      = CollectionFactory.createConcurrentWeakKeySoftValueMap();
    private boolean myUseSpellCheck;

    @Override
    public @NotNull InspectionProfileWrapper apply(@NotNull InspectionProfile profile) {
      if (!READY) {
        return new InspectionProfileWrapper((InspectionProfileImpl)profile);
      }
      MyInspectionProfileWrapper wrapper = myWrappers.get(profile);
      return wrapper == null
             ? ConcurrencyUtil.cacheOrGet(myWrappers, profile, new MyInspectionProfileWrapper(profile, myUseSpellCheck))
             : wrapper;
    }

    public void setUseSpellCheck(boolean useSpellCheck) {
      myUseSpellCheck = useSpellCheck;
    }
  }

  private static class MyInspectionProfileWrapper extends InspectionProfileWrapper {
    private final boolean myUseSpellCheck;

    MyInspectionProfileWrapper(@NotNull InspectionProfile inspectionProfile, boolean useSpellCheck) {
      super((InspectionProfileImpl)inspectionProfile);
      myUseSpellCheck = useSpellCheck;
    }

    @Override
    public boolean isToolEnabled(HighlightDisplayKey key, PsiElement element) {
      return (key != null && SPELL_CHECK_TOOLS.containsKey(key.getShortName()) ? myUseSpellCheck : super.isToolEnabled(key, element));
    }
  }
}
