/*
 * Created by IntelliJ IDEA.
 * User: Alexander.Kitaev
 * Date: 24.07.2006
 * Time: 16:58:22
 */
package org.jetbrains.idea.svn.dialogs.browser;

import javax.swing.*;
import java.awt.*;

class MessageBoxCellRenderer extends DefaultListCellRenderer {

  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    if (value != null) {
      String message = (String) value;
      message = message.replace('\r', '|');
      message = message.replace('\n', '|');
      if (message.length() > 50) {
        message = message.substring(0, 50) + "[...]";
      }
      value = message;
    }
    return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
  }
}