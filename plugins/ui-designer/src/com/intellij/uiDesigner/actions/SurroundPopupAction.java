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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.componentTree.ComponentTree;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class SurroundPopupAction extends AbstractGuiEditorAction {
  private final SurroundActionGroup myActionGroup = new SurroundActionGroup();

  protected void actionPerformed(final GuiEditor editor, final List<RadComponent> selection, final AnActionEvent e) {
    final ListPopup groupPopup = JBPopupFactory.getInstance()
      .createActionGroupPopup(UIDesignerBundle.message("surround.with.popup.title"), myActionGroup, e.getDataContext(),
                              JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, true);

    final JComponent component = (JComponent)e.getData(PlatformDataKeys.CONTEXT_COMPONENT);
    if (component instanceof ComponentTree) {
      groupPopup.show(JBPopupFactory.getInstance().guessBestPopupLocation(component));
    }
    else {
      RadComponent selComponent = selection.get(0);
      FormEditingUtil.showPopupUnderComponent(groupPopup, selComponent);
    }
  }

  protected void update(@NotNull GuiEditor editor, final ArrayList<RadComponent> selection, final AnActionEvent e) {
    e.getPresentation().setEnabled(selection.size() > 0);
  }
}
