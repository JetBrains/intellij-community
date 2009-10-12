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
package com.intellij.uiDesigner.quickFixes;

import org.jetbrains.annotations.NotNull;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * [vova] This class should be inner but due to bugs in "beta" generics compiler
 * I need to use "static" modifier.
 *
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class FocusListenerImpl extends FocusAdapter{
  private final QuickFixManager myManager;

  public FocusListenerImpl(@NotNull final QuickFixManager manager) {
    myManager = manager;
  }

  public void focusGained(final FocusEvent e) {
    if(!e.isTemporary()){
      myManager.updateIntentionHintVisibility();
    }
  }

  public void focusLost(final FocusEvent e) {
    if(!(e.isTemporary())){
      myManager.hideIntentionHint();
    }
  }
}
