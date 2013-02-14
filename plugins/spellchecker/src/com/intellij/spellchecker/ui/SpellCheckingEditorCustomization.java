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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.ui.SimpleEditorCustomization;
import com.intellij.util.Function;
import com.intellij.util.containers.WeakHashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Allows to enforce editors to use/don't use spell checking ignoring user-defined spelling inspection settings.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since Aug 20, 2010 3:54:42 PM
 */
public class SpellCheckingEditorCustomization extends SimpleEditorCustomization {

  public static final SpellCheckingEditorCustomization ENABLED = new SpellCheckingEditorCustomization(true);
  public static final SpellCheckingEditorCustomization DISABLED = new SpellCheckingEditorCustomization(false);

  private static final Set<LocalInspectionToolWrapper> SPELL_CHECK_TOOLS = new HashSet<LocalInspectionToolWrapper>();
  private static final boolean READY = init();

  @NotNull
  public static SpellCheckingEditorCustomization getInstance(boolean enabled) {
    return enabled ? ENABLED : DISABLED;
  }

  private SpellCheckingEditorCustomization(boolean enabled) {
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
        SPELL_CHECK_TOOLS.add(new LocalInspectionToolWrapper(tool));
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

    Function<InspectionProfileWrapper, InspectionProfileWrapper> strategy = file.getUserData(InspectionProfileWrapper.CUSTOMIZATION_KEY);
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
  
  private static class MyInspectionProfileStrategy implements Function<InspectionProfileWrapper, InspectionProfileWrapper> {
    
    private final Map<InspectionProfileWrapper, MyInspectionProfileWrapper> myWrappers
      = new WeakHashMap<InspectionProfileWrapper, MyInspectionProfileWrapper>();
    private boolean myUseSpellCheck;
    
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
    public List<LocalInspectionToolWrapper> getHighlightingLocalInspectionTools(PsiElement element) {
      Set<LocalInspectionToolWrapper> result = new THashSet<LocalInspectionToolWrapper>(myDelegate.getHighlightingLocalInspectionTools(element), new TObjectHashingStrategy<LocalInspectionToolWrapper>() {
        @Override
        public int computeHashCode(LocalInspectionToolWrapper object) {
          return object.getShortName().hashCode();
        }

        @Override
        public boolean equals(LocalInspectionToolWrapper o1, LocalInspectionToolWrapper o2) {
          return o1.getShortName().equals(o2.getShortName());
        }
      });
      
      if (myUseSpellCheck) {
        result.removeAll(SPELL_CHECK_TOOLS);
        result.addAll(SPELL_CHECK_TOOLS);
      }
      else {
        result.removeAll(SPELL_CHECK_TOOLS);
      }
      return new ArrayList<LocalInspectionToolWrapper>(result);
    }

    public void setUseSpellCheck(boolean useSpellCheck) {
      myUseSpellCheck = useSpellCheck;
    }
  }
}
