package org.jetbrains.plugins.terminal;

import com.google.common.base.Predicate;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.TabbedTerminalWidget;
import com.jediterm.terminal.ui.TerminalAction;
import com.jediterm.terminal.ui.TerminalWidget;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author traff
 */
public class JBTabbedTerminalWidget extends TabbedTerminalWidget {

  public JBTabbedTerminalWidget(@NotNull SettingsProvider settingsProvider, @NotNull Predicate<TerminalWidget> createNewSessionAction) {
    super(settingsProvider, createNewSessionAction);

    convertActions(this, getActions());
  }

  public static void convertActions(JComponent component, List<TerminalAction> actions) {
    for (final TerminalAction action : actions) {
      AnAction a = new DumbAwareAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          action.perform(e.getInputEvent() instanceof KeyEvent? (KeyEvent)e.getInputEvent() : null);
        }
      };
      a.registerCustomShortcutSet(action.getKeyCode(), action.getModifiers(), component);
    }
  }

  @Override
  protected JediTermWidget createInnerTerminalWidget(SettingsProvider settingsProvider) {
    return new JBTerminalWidget(settingsProvider);
  }
}
