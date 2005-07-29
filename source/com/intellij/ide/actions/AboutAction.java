package com.intellij.ide.actions;

import com.intellij.Patches;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.license.LicenseManager;
import com.intellij.ide.license.ui.LicenseUrls;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.AnimatingSurface;
import com.intellij.util.ImageLoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class AboutAction extends AnAction {
  private static final String[] months = new String[]{"January", "February", "March", "April", "May", "June", "July",
                                                      "August", "September", "October", "November", "December"};

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(!SystemInfo.isMacSystemMenu);
  }

  public void actionPerformed(AnActionEvent e) {
    Window window = WindowManager.getInstance().suggestParentWindow((Project)e.getDataContext().getData(DataConstants.PROJECT));

    showAboutDialog(window);
  }

  public static void showAbout() {
    Window window = WindowManager.getInstance().suggestParentWindow(
      (Project)DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT));

    showAboutDialog(window);
  }

  private static void showAboutDialog(Window window) {
    ApplicationInfoEx appInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
    JPanel mainPanel = new JPanel(new BorderLayout());
    final JComponent closeListenerOwner;
    if (appInfo.showLicenseeInfo()) {
      final Image image = ImageLoader.loadFromResource(appInfo.getAboutLogoUrl());
      final InfoSurface infoSurface = new InfoSurface(image);
      infoSurface.setPreferredSize(new Dimension(image.getWidth(null), image.getHeight(null)));
      mainPanel.add(infoSurface, BorderLayout.NORTH);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          infoSurface.start();
        }
      });
      closeListenerOwner = infoSurface;
    }
    else {
      mainPanel.add(new JLabel(IconLoader.getIcon(appInfo.getAboutLogoUrl())), BorderLayout.NORTH);
      closeListenerOwner = mainPanel;
    }

    final JDialog dialog;
    if (window instanceof Dialog) {
      dialog = new JDialog((Dialog)window);
    }
    else {
      dialog = new JDialog((Frame)window);
    }
    dialog.setUndecorated(true);
    dialog.setContentPane(mainPanel);
    dialog.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiers() == 0) {
          dialog.dispose();
        }
      }
    });

    final long showTime = System.currentTimeMillis();
    final long delta = Patches.APPLE_BUG_ID_3716865 ? 100 : 0;

    dialog.addWindowFocusListener(new WindowFocusListener() {
      public void windowGainedFocus(WindowEvent e) {}

      public void windowLostFocus(WindowEvent e) {
        long eventTime = System.currentTimeMillis();
        if (eventTime - showTime > delta && e.getOppositeWindow() != e.getWindow()) {
          dialog.dispose();
        }
      }
    });

    closeListenerOwner.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (!e.isConsumed()) {
          dialog.dispose();
          e.consume();
        }
      }
    });

    dialog.pack();

    dialog.setLocationRelativeTo(window);
    dialog.setVisible(true);
  }

  private static class InfoSurface extends AnimatingSurface {
    final Color col = new Color(69, 86, 156, 200);
    final Color linkCol = new Color(255, 255, 128, 200);
    final int UP = 0;
    final int DOWN = 1;
    private Image myImage;
    private float myAlpha;
    private int myAlphaDirection = UP;
    private Font myFont;
    private Font myBoldFont;
    private String q[] = new String[12];
    private int linkY;
    private int linkWidth;
    private boolean inLink = false;

    public InfoSurface(Image image) {
      myImage = image;


      myAlpha = 0f;
      setOpaque(true);
      setBackground(col);
      ApplicationInfoEx ideInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
      Calendar cal = ideInfo.getBuildDate();
      q[0] = ideInfo.getFullApplicationName();
      q[1] = "Build #" + ideInfo.getBuildNumber();
      q[2] = "Built on " + months[cal.get(Calendar.MONTH)] + " " + cal.get(Calendar.DATE) + ", " +
             cal.get(Calendar.YEAR);
      q[3] = LicenseManager.getInstance().licensedToMessage();
      q[4] = LicenseManager.getInstance().licensedRestrictionsMessage();
      q[5] = "";
      {
        final Properties properties = System.getProperties();
        q[6] = "JDK: " + properties.getProperty("java.version", "unknown");
        q[7] = "VM: " + properties.getProperty("java.vm.name", "unknown");
        q[8] = "Vendor: " + properties.getProperty("java.vendor", "unknown");
      }
      q[9] = "";
      q[10] = "JetBrains s.r.o.";
      q[11] = LicenseUrls.getCompanyUrl();
      addMouseListener(new MouseAdapter() {
        public void mousePressed(MouseEvent event) {
          if (inLink) {
            event.consume();
            BrowserUtil.launchBrowser(LicenseUrls.getCompanyUrl());
          }
        }
      });
      addMouseMotionListener(new MouseMotionAdapter() {
        public void mouseMoved(MouseEvent event) {
          if (
            event.getPoint().x > 20 && event.getPoint().y >= linkY &&
            event.getPoint().x < 20 + linkWidth && event.getPoint().y < linkY + 10
          ) {
            if (!inLink) {
              setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              inLink = true;
            }
          }
          else {
            if (inLink) {
              setCursor(Cursor.getDefaultCursor());
              inLink = false;
            }
          }
        }
      });
    }

    public class TextRenderer {
      private final int xBase;
      private final int yBase;
      private final int w;
      private final int h;
      private final Graphics2D g2;

      private int x = 0, y = 0;
      private FontMetrics fontmetrics;
      private int fontAscent;
      private int fontHeight;
      private Font font;

      public class Overflow extends Exception { }

      public TextRenderer(final int xBase, final int yBase, final int w, final int h, final Graphics2D g2) {
        this.xBase = xBase;
        this.yBase = yBase;
        this.w = w;
        this.h = h;
        this.g2 = g2;
        g2.fillRect(xBase, yBase, w, h);
        g2.draw3DRect(xBase, yBase, w, h, true);
      }



      public void render(int indentX, int indentY, String[] q) throws Overflow {
        x = indentX;
        y = indentY;
        g2.setColor(Color.white);
        for (int i = 0; i < q.length; i++) {
          final String s = q[i];
          setFont (i == 0 || i >= q.length - 2 ? myBoldFont : myFont);
          if (i == q.length - 1) {
             g2.setColor(linkCol);
             linkY = yBase + y - fontAscent;
             FontMetrics metrics = g2.getFontMetrics(font);
             linkWidth = metrics.stringWidth (s);
          }
          renderString(s, indentX);
          lineFeed (indentX);
        }
      }

      private void renderString(final String s, final int indentX) throws Overflow {
        final java.util.List<String> words = StringUtil.split(s, " ");
        for (String word : words) {
          int wordWidth = fontmetrics.stringWidth(word);
          if (x + wordWidth >= w) {
            lineFeed(indentX);
          }
          else {
            char c = ' ';
            final int cW = fontmetrics.charWidth(c);
            if (x + cW < w) {
              g2.drawChars(new char[]{c}, 0, 1, xBase + x, yBase + y);
              x += cW;
            }
          }
          renderWord(word, indentX);
        }
      }

      private void renderWord(final String s, final int indentX) throws Overflow {
        for (int j = 0; j != s.length(); ++ j) {
          final char c = s.charAt(j);
          final int cW = fontmetrics.charWidth(c);
          if (x + cW >= w) {
            lineFeed(indentX);
          }
          g2.drawChars(new char[]{c}, 0, 1, xBase + x, yBase + y);
          x += cW;
        }
      }

      private void lineFeed(int indent) throws Overflow {
        x = indent;
        y += fontHeight;
        if (y + fontHeight >= h)
          throw new Overflow();
      }

      private void setFont(Font font) {
        this.font = font;
        fontmetrics =  g2.getFontMetrics(font);
        g2.setFont (font);
        fontAscent = fontmetrics.getAscent();
        fontHeight = fontmetrics.getHeight();
      }
    }

    public void render(int w, int h, Graphics2D g2) {
      AlphaComposite ac = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha);
      g2.setComposite(ac);

      Font labelFont = UIManager.getFont("Label.font");
      Loop:
      for (int labelSize = 10; labelSize != 6; labelSize -= 1) {
        g2.setPaint(col);
        g2.drawImage(myImage, 0, 0, this);
        g2.setColor(col);
        int startY = (int)(-250 * (1.0f - myAlpha) + 20);
        TextRenderer renderer = new TextRenderer(15, startY, 190, 250, g2);
        g2.setComposite(AlphaComposite.Src);
        myFont = labelFont.deriveFont(Font.PLAIN, labelSize);
        myBoldFont = labelFont.deriveFont(Font.BOLD, labelSize+1);
        try {
          renderer.render (20, 25, q);
          break;
        }
        catch (TextRenderer.Overflow _) {
          continue Loop;
        }
      }
    }

    public void reset(int w, int h) { }

    public void step(int w, int h) {
      if (myAlphaDirection == UP) {
        if ((myAlpha += 0.2) > .99) {
          myAlphaDirection = DOWN;
          myAlpha = 1.0f;
        }
      }
      else if (myAlphaDirection == DOWN) {
        stop();
      }
    }
  }
}
