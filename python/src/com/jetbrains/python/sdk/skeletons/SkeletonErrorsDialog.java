/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.sdk.skeletons;

import com.intellij.ui.components.JBScrollPane;
import com.jetbrains.python.PyBundle;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

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
    final Color foreground = getParent().getForeground();
    final Color background = getParent().getBackground();
    if (foreground != null && background != null) {
      sb.append("text='").append(getHTMLColor(foreground)).append("' ");
      sb.append("bgcolor='").append(getHTMLColor(background)).append("'");
    }
    sb.append(">");

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
