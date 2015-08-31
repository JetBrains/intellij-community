/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.eval;

import com.intellij.find.FindSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import org.intellij.plugins.xpathView.Config;
import org.intellij.plugins.xpathView.HistoryElement;
import org.intellij.plugins.xpathView.ui.InputExpressionDialog;
import org.jetbrains.annotations.NotNull;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class EvalExpressionDialog extends InputExpressionDialog<EvalFormPanel> {

    public EvalExpressionDialog(Project project, Config settings, HistoryElement[] history) {
        super(project, settings, history, new EvalFormPanel());
        setTitle("Evaluate XPath Expression");
        setOKButtonText("Evaluate");
    }

    protected void init() {
        final ToolWindow findWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.FIND);
        final boolean available = findWindow != null && findWindow.isAvailable();

        myForm.getNewTabCheckbox().setEnabled(available);
        myForm.getNewTabCheckbox().setSelected(FindSettings.getInstance().isShowResultsInSeparateView());

        myForm.getHighlightCheckbox().setSelected(mySettings.HIGHLIGHT_RESULTS);
        myForm.getHighlightCheckbox().addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                updateOkAction();
            }
        });
        myForm.getUsageViewCheckbox().setSelected(mySettings.SHOW_USAGE_VIEW);
        myForm.getUsageViewCheckbox().addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                myForm.getNewTabCheckbox().setEnabled(available && myForm.getUsageViewCheckbox().isSelected());
                updateOkAction();
            }
        });

        super.init();
    }

    protected boolean isOkEnabled() {
        final EvalFormPanel form = getForm();
        final boolean b = form.getHighlightCheckbox().isSelected() || form.getUsageViewCheckbox().isSelected();
        return b & super.isOkEnabled();
    }

    protected void doOKAction() {
        final EvalFormPanel form = getForm();
        if (form.getNewTabCheckbox().isEnabled()) {
            FindSettings.getInstance().setShowResultsInSeparateView(form.getNewTabCheckbox().isSelected());
        }

        mySettings.HIGHLIGHT_RESULTS = form.getHighlightCheckbox().isSelected();
        mySettings.SHOW_USAGE_VIEW = form.getUsageViewCheckbox().isSelected();
        super.doOKAction();
    }

    @NotNull
    protected String getPrivateDimensionServiceKey() {
        return "XPathView.InputDialog.DIMENSION_SERVICE_KEY";
    }
}
