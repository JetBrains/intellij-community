package com.intellij.ui;

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;
import com.intellij.ide.license.LicenseManager;

import javax.swing.*;
import java.awt.*;

public class Splash extends JWindow {
  private static final Color ideaDARK_BLUE = new Color(0, 35, 135);
  private Icon myImage;
  private JLabel myLabel;
  private boolean myShowLicenseeInfo;

  public Splash(String imageName) {
    Icon originalImage = IconLoader.getIcon(imageName);
    myShowLicenseeInfo = ApplicationInfoImpl.getShadowInstance().showLicenseeInfo();
    myImage = new MyIcon(originalImage);
    myLabel = new JLabel(myImage);
    Container contentPane = getContentPane();
    contentPane.setLayout(new BorderLayout());
    contentPane.add(myLabel, BorderLayout.CENTER);
    Dimension size = getPreferredSize();
    setSize(size);
    pack();
    setLocationRelativeTo(null);
  }

  public void show() {
    super.show();
    myLabel.paintImmediately(0, 0, myImage.getIconWidth(), myImage.getIconHeight());
  }

  private final class MyIcon implements Icon {
    private Icon myOriginalIcon;

    public MyIcon(Icon originalIcon) {
      myOriginalIcon = originalIcon;
    }

    public void paintIcon(Component c, Graphics g, int x, int y) {
      yeild();
      myOriginalIcon.paintIcon(c, g, x, y);

      if (myShowLicenseeInfo) {
        g.setFont(new Font(UIUtil.ARIAL_FONT_NAME, Font.BOLD, 11));
        g.setColor(ideaDARK_BLUE);
        g.drawString(LicenseManager.getInstance().licensedToMessage(), x + 20, y + getIconHeight() - 60);
        g.drawString(LicenseManager.getInstance().licensedRestrictionsMessage(), x + 20, y + getIconHeight() - 40);
      }
    }

    private void yeild() {
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException e) {
      }
    }

    public int getIconWidth() {
      return myOriginalIcon.getIconWidth();
    }

    public int getIconHeight() {
      return myOriginalIcon.getIconHeight();
    }
  }
}