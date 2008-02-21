package com.intellij.usages.impl.rules;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.RuleAction;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.UsageFilteringRuleProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class JavaUsageFilteringRuleProvider implements UsageFilteringRuleProvider {
  @NotNull
  public UsageFilteringRule[] getActiveRules(final Project project) {
    final List<UsageFilteringRule> rules = new ArrayList<UsageFilteringRule>();
    if (!JavaUsageViewSettings.getInstance().SHOW_IMPORTS) {
      rules.add(new ImportFilteringRule());
    }
    return rules.toArray(new UsageFilteringRule[rules.size()]);
  }

  @NotNull
  public AnAction[] createFilteringActions(final UsageView view) {
    final UsageViewImpl impl = (UsageViewImpl)view;
    if (view.getPresentation().isCodeUsages()) {
      final JComponent component = view.getComponent();
      final ShowImportsAction showImportsAction = new ShowImportsAction(impl);
      showImportsAction
          .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK)), component);

      impl.scheduleDisposeOnClose(new Disposable() {
        public void dispose() {
          showImportsAction.unregisterCustomShortcutSet(component);
        }
      });
      return new AnAction[] { showImportsAction };
    }
    else {
      return AnAction.EMPTY_ARRAY;
    }
  }

  private static class ShowImportsAction extends RuleAction {
    public ShowImportsAction(UsageViewImpl view) {
      super(view, UsageViewBundle.message("action.show.import.statements"), IconLoader.getIcon("/actions/showImportStatements.png"));
    }

    protected boolean getOptionValue() {
      return JavaUsageViewSettings.getInstance().SHOW_IMPORTS;
    }

    protected void setOptionValue(boolean value) {
      JavaUsageViewSettings.getInstance().SHOW_IMPORTS = value;
    }
  }

}
