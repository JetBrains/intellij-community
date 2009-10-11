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

package com.intellij.uiDesigner.actions;

import com.intellij.uiDesigner.CaptionSelection;
import com.intellij.uiDesigner.GridChangeUtil;
import com.intellij.uiDesigner.UIDesignerBundle;

/**
 * @author yole
*/
public final class SplitAction extends RowColumnAction {
  public SplitAction() {
    super(UIDesignerBundle.message("action.split.column"), "/com/intellij/uiDesigner/icons/splitColumn.png",
          UIDesignerBundle.message("action.split.row"), "/com/intellij/uiDesigner/icons/splitRow.png");
  }

  protected void actionPerformed(CaptionSelection selection) {
    GridChangeUtil.splitCell(selection.getContainer(), selection.getFocusedIndex(), selection.isRow());
  }
}
