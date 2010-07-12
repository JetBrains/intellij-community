/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class SimpleErrorReportSubmitter extends ErrorReportSubmitter {
  public String getReportActionText() {
    return "Error Report Information";
  }

  public SubmittedReportInfo submit(IdeaLoggingEvent[] events, Component parentComponent) {
    final IdeaPluginDescriptor plugin = PluginManager.getPlugin(getPluginDescriptor().getPluginId());

    assert plugin != null;
    final String title = plugin.getName() + " - " + plugin.getVersion();

    final String vendorEmail = plugin.getVendorEmail();
    final String subject = title + " Exception";
    final String mailto = "mailto:" + encode(vendorEmail) + "?Subject=" + encode(subject) /*+
                "&body=" + encode("Stacktrace:\n" + events[0].getThrowableText())*/;

    final String weblink = "http://www.intellij.net/forums/forum.jsp?forum=18";
    final String website = plugin.getUrl();

    final String font = new JLabel().getFont().getName();

    String message = "<html><span style='font: " + font + "; font-size:smaller'>" +
            "To report an exception, please post the exception stacktrace and a short description " +
            "of how it can be reproduced to the IntelliJ " +
            "<a href='" + weblink + "'>plugin web forum</a> or the corresponding newsgroup, " +
            "and/or send an email containing that information to " +
            "<a href='" + mailto + "'>" + vendorEmail + "</a>." +
            (website != null ? "<br><br>Please also check the plugin's <a href='" + website + "'>website</a> for more information." : "") +
            "</span></html>";

    final JEditorPane pane = new JEditorPane("text/html", message);
    pane.setEditable(false);
    pane.setPreferredSize(new Dimension(410, website != null ? 110 : 80));
    pane.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          BrowserUtil.launchBrowser(e.getURL().toExternalForm());
        }
      }
    });

    new MyMessageDialog(parentComponent, title, Messages.getErrorIcon(), pane).show();

    return new SubmittedReportInfo(null, null, SubmittedReportInfo.SubmissionStatus.FAILED);
  }

  private String encode(String link) {
    final String enc = System.getProperty("file.encoding");
    try {
      return URLEncoder.encode(link, enc).replaceAll("\\+", "%20");
    } catch (UnsupportedEncodingException e) {
      throw new Error(e);
    }
  }

  private static class MyMessageDialog extends DialogWrapper {
    protected JComponent myMessage;
    protected Icon myIcon;

    public MyMessageDialog(Component parent, String title, Icon icon, JComponent message) {
      super(parent, false);
      _init(title, message, icon);
    }

    private void _init(String title, JComponent message, Icon icon) {
      setTitle(title);
      myMessage = message;
      myIcon = icon;
      setButtonsAlignment(SwingUtilities.CENTER);
      init();

      myMessage.setBackground(getContentPane().getBackground());
    }

    protected Action[] createActions() {
      return new Action[]{ getOKAction() };
    }

    protected JComponent createNorthPanel() {
      JPanel panel = new JPanel(new BorderLayout(15, 0));
      if (myIcon != null) {
        JLabel iconLabel = new JLabel(myIcon);
        Container container = new Container();
        container.setLayout(new BorderLayout());
        container.add(iconLabel, BorderLayout.NORTH);
        panel.add(container, BorderLayout.WEST);
      }

      if (myMessage != null) {
        panel.add(myMessage, BorderLayout.CENTER);
      }
      return panel;
    }

    @Nullable
    protected JComponent createCenterPanel() {
      return null;
    }
  }
}
