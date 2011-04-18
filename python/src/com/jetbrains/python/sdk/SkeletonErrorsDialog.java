package com.jetbrains.python.sdk;

import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBScrollPane;
import com.jetbrains.python.PyBundle;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

import static java.lang.Math.*;

public class SkeletonErrorsDialog extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private JBScrollPane myScroller;
  private JTextPane myMessagePane;

  public SkeletonErrorsDialog(Map<String, List<String>> errors, List<String> failed_sdks) {
    setContentPane(contentPane);
    setModal(true);
    getRootPane().setDefaultButton(buttonOK);

    buttonOK.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        dispose();
      }
    });

    // fill data
    myMessagePane.setContentType("text/html");
    myMessagePane.setBorder(new EmptyBorder(0, 0, 0, 0));
    StringBuilder sb = new StringBuilder("<html><body style='margin: 4pt;' ");
    sb.append("text='").append(getHTMLColor(getParent().getForeground())).append("' ");
    sb.append("bgcolor='").append(getHTMLColor(getParent().getBackground())).append("'>");

    if (failed_sdks.size() > 0) {
      sb.append("<h1>").append(PyBundle.message("sdk.error.dialog.failed.sdks")).append("</h1>");
      sb.append("<ul>");
      for (String sdk_name : failed_sdks) {
        sb.append("<li>").append(sdk_name).append("</li>");
      }
      sb.append("</ul><br>");
    }

    if (errors.size() > 0) {
      sb.append("<h1>").append(PyBundle.message("sdk.error.dialog.failed.modules")).append("</h1>");
      for (String sdk_name : errors.keySet()) {
        sb.append("<b>").append(sdk_name).append("</b><br>");
        sb.append("<ul>");
        for (String module_name : errors.get(sdk_name)) {
          sb.append("<li>").append(module_name).append("</li>");
        }
        sb.append("</ul>");
      }
      sb.append(PyBundle.message("sdk.error.dialog.were.blacklisted"));
    }

    sb.append("</body></html>");
    myMessagePane.setText(sb.toString());

    setTitle(PyBundle.message("sdk.error.dialog.problems"));

    pack();
    setLocationRelativeTo(getParent());
  }

  private static String getHTMLColor(Color color) {
    StringBuilder sb = new StringBuilder("#");
    sb.append(Integer.toHexString(color.getRGB() & 0xffffff));
    return sb.toString();
  }
}
