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

import com.intellij.find.FindSettings;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.usages.*;
import org.intellij.plugins.xpathView.*;
import org.intellij.plugins.xpathView.support.XPathSupport;
import org.intellij.plugins.xpathView.ui.InputExpressionDialog;
import org.jaxen.JaxenException;
import org.jaxen.XPathSyntaxException;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class FindByXPathAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        e.getPresentation().setEnabled(project != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        final Module module = e.getData(PlatformCoreDataKeys.MODULE);

        if (project != null) {
            executeSearch(project, module);
        }
    }

    private void executeSearch(@NotNull final Project project, final Module module) {
        final Config settings = XPathAppComponent.getInstance().getConfig();
        final XPathProjectComponent projectComponent = XPathProjectComponent.getInstance(project);

        final FindByExpressionDialog dlg =
                new FindByExpressionDialog(project, settings, projectComponent.getFindHistory(), module);

        if (!dlg.show(null)) {
            return;
        }

        final SearchScope scope = dlg.getScope();
        settings.MATCH_RECURSIVELY = dlg.isMatchRecursively();
        settings.SEARCH_SCOPE = dlg.getScope();

        final InputExpressionDialog.Context context = dlg.getContext();
        projectComponent.addFindHistory(context.input);

        final String expression = context.input.expression;
        if (!validateExpression(project, expression)) {
            return;
        }

        final UsageViewPresentation presentation = new UsageViewPresentation();
        presentation.setTargetsNodeText(settings.MATCH_RECURSIVELY ? XPathBundle.message("list.item.xpath.pattern")
                                                                   : XPathBundle.message("list.item.xpath.expression"));
        presentation.setCodeUsages(false);
        presentation.setCodeUsagesString(XPathBundle.message("list.item.found.matches.in", scope.getName()));
        presentation.setNonCodeUsagesString(XPathBundle.message("list.item.result"));
        presentation.setUsagesString(XPathBundle.message("results.matching.0", expression));
        presentation.setTabText(StringUtil.shortenTextWithEllipsis(XPathBundle.message("tab.title.xpath", expression), 60, 0, true));
        presentation.setScopeText(scope.getName());

        presentation.setOpenInNewTab(FindSettings.getInstance().isShowResultsInSeparateView());

        final FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation(presentation);
        processPresentation.setShowPanelIfOnlyOneUsage(true);
        processPresentation.setShowNotFoundMessage(true);

        final XPathEvalAction.MyUsageTarget usageTarget = new XPathEvalAction.MyUsageTarget(context.input.expression, null);
        final UsageTarget[] usageTargets = new UsageTarget[]{ usageTarget };

        final Factory<UsageSearcher> searcherFactory =
          () -> new XPathUsageSearcher(project, context.input, scope, settings.MATCH_RECURSIVELY);
        final UsageViewManager.UsageViewStateListener stateListener = new UsageViewManager.UsageViewStateListener() {
            @Override
            public void usageViewCreated(@NotNull UsageView usageView) {
                usageView.addButtonToLowerPane(new MyEditExpressionAction(project, module),
                                               XPathBundle.message("button.edit.expression.with.mnemonic"));
            }

            @Override
            public void findingUsagesFinished(UsageView usageView) {
            }
        };
        UsageViewManager.getInstance(project).searchAndShowUsages(
                usageTargets,
                searcherFactory,
                processPresentation,
                presentation,
                stateListener);
    }

    private class MyEditExpressionAction extends XPathEvalAction.EditExpressionAction {
        private final Project myProject;
        private final Module myModule;

        MyEditExpressionAction(Project project, Module module) {
            myProject = project;
            myModule = module;
        }

        @Override
        protected void execute() {
            executeSearch(myProject, myModule);
        }
    }

    private static boolean validateExpression(Project project, String expression) {
        try {
          XPathSupport.getInstance().createXPath(null, expression, Collections.emptyList());
            return true;
        } catch (XPathSyntaxException e) {
            Messages.showErrorDialog(project, e.getMultilineMessage(), XPathBundle.message("dialog.title.xpath.syntax.error")); //NON-NLS
        } catch (JaxenException e) {
            Messages.showErrorDialog(project, e.getMessage(), XPathBundle.message("dialog.title.xpath.error"));
        }
        return false;
    }
}