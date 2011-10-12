/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.uiDesigner.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.TextRange;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.inspections.SplitterFactory;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IProperty;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.util.Consumer;

/**
 * @author yole
 */
public class FormSpellCheckingInspection extends StringDescriptorInspection {
  public static final String SHORT_NAME = "SpellCheckingInspection";

  public FormSpellCheckingInspection() {
    super(SHORT_NAME);
  }

  @Override
  protected void checkStringDescriptor(Module module,
                                       final IComponent component,
                                       final IProperty prop,
                                       StringDescriptor descriptor,
                                       final FormErrorCollector collector) {
    final String value = descriptor.getResolvedValue();
    if (value == null) {
      return;
    }
    final SpellCheckerManager manager = SpellCheckerManager.getInstance(module.getProject());
    SplitterFactory.getInstance().getPlainTextSplitter().split(value, TextRange.allOf(value), new Consumer<TextRange>() {
      @Override
      public void consume(TextRange textRange) {
        String word = textRange.substring(value);
        if (manager.hasProblem(word)) {
          collector.addError(getID(), component, prop, "Typo in word '" + word + "'", null);
        }
      }
    });
  }
}
