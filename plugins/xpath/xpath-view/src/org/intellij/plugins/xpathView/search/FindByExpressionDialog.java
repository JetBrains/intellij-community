/*
 * Copyright 2006 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.search;

import com.intellij.find.FindBundle;
import com.intellij.find.FindSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import org.intellij.plugins.xpathView.Config;
import org.intellij.plugins.xpathView.HistoryElement;
import org.intellij.plugins.xpathView.ui.InputExpressionDialog;
import org.intellij.plugins.xpathView.ui.Mode;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class FindByExpressionDialog extends InputExpressionDialog<FindFormPanel> {

    public FindByExpressionDialog(Project parent, Config settings, HistoryElement[] history, Module module) {
        super(parent, settings, history, new FindFormPanel(parent, module, settings.SEARCH_SCOPE));
        setTitle("Find by XPath Expression");
        setOKButtonText(FindBundle.message("find.button"));
    }

    protected void init() {
        final ToolWindow findWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.FIND);
        final boolean available = findWindow != null && findWindow.isAvailable();

        myForm.getNewTabCheckbox().setEnabled(available);
        myForm.getNewTabCheckbox().setSelected(FindSettings.getInstance().isShowResultsInSeparateView());

        myForm.getScopePanel().addPropertyChangeListener("scope", new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                updateOkAction();
            }
        });

        myForm.getMatchEachNode().setSelected(mySettings.MATCH_RECURSIVELY);
        myForm.getMatchRootNode().setSelected(!mySettings.MATCH_RECURSIVELY);

        Disposer.register(myDisposable, myForm);
        super.init();
    }


    protected void setModeImpl(Mode mode) {
        myForm.getOptionsPanel().setVisible(mode == Mode.ADVANCED);
        super.setModeImpl(mode);
    }

    protected boolean isOkEnabled() {
        return myForm.getScope().isValid() && super.isOkEnabled();
    }

    protected void doOKAction() {
        if (myForm.getNewTabCheckbox().isEnabled()) {
            FindSettings.getInstance().setShowResultsInSeparateView(myForm.getNewTabCheckbox().isSelected());
        }

        super.doOKAction();
    }

    @NotNull
    protected String getPrivateDimensionServiceKey() {
        return "XPathView.FindDialog.DIMENSION_SERVICE_KEY";
    }

    public SearchScope getScope() {
        return myForm.getScopePanel().getSearchScope();
    }

    public boolean isMatchRecursively() {
        return myForm.getMatchEachNode().isSelected();
    }
}
