package org.jetbrains.plugins.terminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.RegionPainter;
import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.SettingsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyListener;

public class JBTerminalWidget extends JediTermWidget implements Disposable{

  public JBTerminalWidget(JBTerminalSystemSettingsProvider settingsProvider, Disposable parent) {
    super(settingsProvider);
    setName("terminal");
    JBTabbedTerminalWidget.convertActions(this, getActions());

    Disposer.register(parent, this);
  }

  @Override
  protected JBTerminalPanel createTerminalPanel(@NotNull SettingsProvider settingsProvider,
                                                @NotNull StyleState styleState,
                                                @NotNull TerminalTextBuffer textBuffer) {
    JBTerminalPanel panel = new JBTerminalPanel((JBTerminalSystemSettingsProvider)settingsProvider, textBuffer, styleState);
    Disposer.register(this, panel);
    return panel;
  }

  @Override
  protected Graphics getComponentGraphics(Graphics graphics) {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
  }

  @Override
  protected TerminalStarter createTerminalStarter(JediTerminal terminal, TtyConnector connector) {
    return new JBTerminalStarter(terminal, connector);
  }

  @Override
  protected JScrollBar createScrollBar() {
    JBScrollBar bar = new JBScrollBar();
    bar.putClientProperty(JBScrollPane.Alignment.class, JBScrollPane.Alignment.RIGHT);
    bar.putClientProperty(JBScrollBar.TRACK, new RegionPainter<Object>() {
      @Override
      public void paint(Graphics2D g, int x, int y, int width, int height, Object object) {
        SubstringFinder.FindResult result = myTerminalPanel.getFindResult();
        if (result != null) {
          int modelHeight = bar.getModel().getMaximum() - bar.getModel().getMinimum();
          int anchorHeight = Math.max(2, height / modelHeight);

          Color color = mySettingsProvider.getTerminalColorPalette()
            .getColor(mySettingsProvider.getFoundPatternColor().getBackground());
          g.setColor(color);
          for (SubstringFinder.FindResult.FindItem r : result.getItems()) {
            int where = height * r.getStart().y / modelHeight;
            g.fillRect(x, y + where, width, anchorHeight);
          }
        }
      }
    });
    return bar;
  }
  
  @Override
  public void dispose() {
  }

  @Override
  protected SearchComponent createSearchComponent() {
    return new SearchComponent() {
      private final SearchTextField myTextField = new SearchTextField(false);
      @Override
      public String getText() {
        return myTextField.getText();
      }

      @Override
      public JComponent getComponent() {
        myTextField.setOpaque(false);
        return myTextField;
      }

      @Override
      public void addDocumentChangeListener(DocumentListener listener) {
        myTextField.addDocumentListener(listener);
      }

      @Override
      public void addKeyListener(KeyListener listener) {
        myTextField.addKeyboardListener(listener);
      }

      @Override
      public void onResultUpdated(SubstringFinder.FindResult result) {
      }

      @Override
      public void nextFindResultItem(SubstringFinder.FindResult.FindItem item) {
      }

      @Override
      public void prevFindResultItem(SubstringFinder.FindResult.FindItem item) {
      }
    };
  }
}
