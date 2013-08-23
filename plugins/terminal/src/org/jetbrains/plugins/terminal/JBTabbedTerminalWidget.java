package org.jetbrains.plugins.terminal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.SystemSettingsProvider;
import com.jediterm.terminal.ui.TabbedTerminalWidget;
import com.jediterm.terminal.ui.TerminalAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * @author traff
 */
public class JBTabbedTerminalWidget extends TabbedTerminalWidget {
  public JBTabbedTerminalWidget(@NotNull SystemSettingsProvider settingsProvider) {
    super(settingsProvider);

    convertActions(this, getActions());
  }

  public static void convertActions(JComponent component, List<TerminalAction> actions) {
    for (final TerminalAction action : actions) {
      AnAction a = new DumbAwareAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          if (e.getInputEvent() instanceof KeyEvent) {
            action.perform((KeyEvent)e.getInputEvent());
          }
          else {
            action.perform(null);
          }
        }
      };
      a.registerCustomShortcutSet(action.getKeyCode(), action.getModifiers(), component);
    }
  }

  @Override
  protected JediTermWidget createInnerTerminalWidget() {
    return new JBTerminalWidget(getSystemSettingsProvider());
  }
}
