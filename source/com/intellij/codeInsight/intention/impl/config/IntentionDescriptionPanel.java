/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

public class IntentionDescriptionPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.config.IntentionDescriptionPanel");
  private JPanel myPanel;

  private JPanel myAfterPanel;
  private JPanel myBeforePanel;
  private JEditorPane myDescriptionBrowser;
  private List<IntentionUsagePanel> myBeforeUsagePanels = new ArrayList<IntentionUsagePanel>();
  private List<IntentionUsagePanel> myAfterUsagePanels = new ArrayList<IntentionUsagePanel>();

  public void reset(IntentionActionMetaData actionMetaData)  {
    try {
      myDescriptionBrowser.setText(loadText(actionMetaData.myDescription));
      myDescriptionBrowser.setPreferredSize(new Dimension(20, 20));

      showUsages(myBeforePanel, myBeforeUsagePanels, actionMetaData.myExampleUsagesBefore);
      showUsages(myAfterPanel, myAfterUsagePanels, actionMetaData.myExampleUsagesAfter);

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myPanel.revalidate();
        }
      });

    }
    catch (IOException e) {
      LOG.error(e);
    }
  }
  public void reset(String intentionCategory)  {
    try {
      String text = "<html><body><font face=\"verdana\" size=\"-1\"><center>You have selected intention category '<b>" + intentionCategory + "</b>'.</center>" +
                    "<center>Clicking the checkbox you can enable/disable all intentions in this category.</center>" +
                    "<center>To enable/disable particular intention, select the intention inside this category.</center>" +
                    "</font></body></html>";

      myDescriptionBrowser.setText(text);
      myDescriptionBrowser.setPreferredSize(new Dimension(20, 20));

      URL beforeURL = getClass().getClassLoader().getResource(getClass().getPackage().getName().replace('.','/') + "/before.java.template");
      showUsages(myBeforePanel, myBeforeUsagePanels, new URL[]{beforeURL});
      URL afterURL = getClass().getClassLoader().getResource(getClass().getPackage().getName().replace('.','/') + "/after.java.template");
      showUsages(myAfterPanel, myAfterUsagePanels, new URL[]{afterURL});

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myPanel.revalidate();
        }
      });
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static void showUsages(final JPanel panel, List<IntentionUsagePanel> usagePanels, URL[] exampleUsages) throws IOException {
    GridBagConstraints gb = null;
    boolean reuse = panel.getComponents().length == exampleUsages.length;
    if (!reuse) {
      disposeUsagePanels(usagePanels);
      panel.setLayout(new GridBagLayout());
      panel.removeAll();
      gb = new GridBagConstraints();
      gb.anchor = GridBagConstraints.NORTHWEST;
      gb.fill = GridBagConstraints.BOTH;
      gb.gridheight = GridBagConstraints.REMAINDER;
      gb.gridwidth = 1;
      gb.gridx = 0;
      gb.gridy = 0;
      gb.insets = new Insets(0,0,0,0);
      gb.ipadx = 5;
      gb.ipady = 5;
      gb.weightx = 1;
      gb.weighty = 1;
    }

    for (int i = 0; i < exampleUsages.length; i++) {
      final URL exampleUsage = exampleUsages[i];

      IntentionUsagePanel usagePanel;
      if (reuse) {
        usagePanel = (IntentionUsagePanel)panel.getComponent(i);
      }
      else {
        usagePanel = new IntentionUsagePanel();
        usagePanels.add(usagePanel);
      }
      usagePanel.reset(loadText(exampleUsage));

      String title = StringUtil.trimEnd(new File(exampleUsage.getFile()).getName(), ".template");
      usagePanel.setBorderText(title);
      if (!reuse) {
        if (i == exampleUsages.length) {
          gb.gridwidth = GridBagConstraints.REMAINDER;
        }
        panel.add(usagePanel, gb);
        gb.gridx++;
      }
    }
  }

  private static String loadText(URL url) throws IOException {
    URLConnection connection = url.openConnection();
    InputStream inputStream = connection.getInputStream();

    final StringBuffer targetText = new StringBuffer();
    final DataOutputStream dataOutput = new DataOutputStream(new OutputStream(){
      public void write(int b) {
        targetText.append((char)b);
      }
    });

    FileUtil.copy(inputStream, dataOutput);
    inputStream.close();

    String text = targetText.toString();
    return text;
  }

  public JPanel getComponent() {
    return myPanel;
  }

  public void dispose() {
    disposeUsagePanels(myBeforeUsagePanels);
    disposeUsagePanels(myAfterUsagePanels);
  }

  private static void disposeUsagePanels(List<IntentionUsagePanel> usagePanels) {
    for (int i = 0; i < usagePanels.size(); i++) {
      final IntentionUsagePanel usagePanel = usagePanels.get(i);
      usagePanel.dispose();
    }
    usagePanels.clear();
  }
}