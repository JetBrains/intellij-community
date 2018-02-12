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

package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.ui.ColoredListCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.intellij.openapi.util.text.StringUtil.shortenTextWithEllipsis;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

class MessageBoxCellRenderer extends ColoredListCellRenderer<String> {

  @Override
  protected void customizeCellRenderer(@NotNull JList<? extends String> list, String value, int index, boolean selected, boolean hasFocus) {
    if (value != null) {
      value = shortenTextWithEllipsis(value.replace('\r', '|').replace('\n', '|'), 50, 0);
    }
    append(notNullize(value), REGULAR_ATTRIBUTES);
  }
}
