package org.jetbrains.plugins.ipnb;

import com.intellij.ui.JBColor;
import javafx.application.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.IpnbEditorUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class IpnbUtils {
  private static int hasFx = 0;

  public static JComponent createLatexPane(@NotNull final String source, int width) {
    final JComponent panel = createHtmlPanel(source, width);

    panel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        final Container parent = panel.getParent();
        final MouseEvent parentEvent = SwingUtilities.convertMouseEvent(panel, e, parent);
        parent.dispatchEvent(parentEvent);
      }
    });
    //TODO: jump to the section (see User Interface#Utilities)

    return panel;
  }

  public static boolean hasFx() {
    if (hasFx == 0) {
      try {
        Platform.setImplicitExit(false);
        hasFx = 1;
      }
      catch (NoClassDefFoundError e) {
        hasFx = 2;
      }
    }
    return hasFx == 1;
  }

  public static JComponent createHtmlPanel(@NotNull final String source, int width) {
    if (hasFx()) {
      return IpnbJfxUtils.createHtmlPanel(source, width);
    }
    return createNonJfxPanel(source);
  }

  public static JComponent createNonJfxPanel(@NotNull final String source) {
    final JTextArea textArea = new JTextArea(source);
    textArea.setLineWrap(true);
    textArea.setEditable(false);
    textArea.setBorder(BorderFactory.createLineBorder(JBColor.lightGray));
    textArea.setBackground(IpnbEditorUtil.getBackground());
    return textArea;
  }

}
