/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.util.Iconable;
import org.jetbrains.annotations.NotNull;

public interface SpellCheckerQuickFix extends LocalQuickFix, Iconable {
  SpellCheckerQuickFix[] EMPTY_ARRAY = new SpellCheckerQuickFix[0];

  @NotNull
  Anchor getPopupActionAnchor();
}
