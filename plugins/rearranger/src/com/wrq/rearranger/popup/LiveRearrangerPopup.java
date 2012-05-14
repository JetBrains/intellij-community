/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.popup;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.wrq.rearranger.LiveRearrangerActionHandler;
import com.wrq.rearranger.entry.RangeEntry;
import com.wrq.rearranger.rearrangement.Emitter;
import com.wrq.rearranger.ruleinstance.RuleInstance;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.IconUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.dnd.DnDConstants;
import java.awt.event.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Contains logic to display a rearrangement popup, allow user to perform drag&drop rearrangement, and (when
 * a keystroke is seen or a mouse click outside the popup occurs) rearrange the code accordingly.
 */
public class LiveRearrangerPopup
  implements IHasScrollPane, ILiveRearranger
{
  private static final Logger LOG = Logger.getInstance("#" + LiveRearrangerPopup.class.getName());
  final RearrangerSettings settings;
  PopupTreeComponent  treeComponent;
  List<RuleInstance> myResultRuleInstances;
  final Window   outerPanel;
  final Document document;
  final PsiFile  psiFile;
  FilePopupEntry myPsiFileEntry;
  private WindowFocusListener windowFocusListener;
  private WindowAdapter       windowAdapter;
  private MouseAdapter        mouseAdapter;
  private KeyEventDispatcher  keyEventDispatcher;
  private Popup               popup;
  //    private JWindow popup;
  TreeDragSource tds;
  TreeDropTarget tdt;
  boolean rearrangementOccurred = false;
  private       FocusAdapter focusAdapter;
  private       JPanel       containerPanel;
  private final Project      project;
  private       Cursor       oldCursor;
  private       MouseAdapter ma2;
  int cursorOffset;
  private Component outerPanelFocusOwner;
  boolean sawKeyPressed = false;

  public LiveRearrangerPopup(RearrangerSettings settings,
                             final FilePopupEntry psiFileEntry,
                             Window outerPanel,
                             Document document,
                             Project project)
  {
    LOG.debug("entered LiveRearrangerPopup constructor");
    this.settings = settings;
    this.outerPanel = outerPanel;
    this.myPsiFileEntry = psiFileEntry;
    this.document = document;
    this.psiFile = null;
    this.project = project;
  }

  private void createFilePopupEntry(final PsiFile psiFile) {
    myPsiFileEntry = new FilePopupEntry() {
      public String getTypeIconName() {
        return "nodes/ppFile";
      }

      public String[] getAdditionalIconNames() {
        return null;
      }

      public JLabel getPopupEntryText(RearrangerSettings settings) {
        return new JLabel(psiFile.getName());
      }
    };
  }

  public LiveRearrangerPopup(RearrangerSettings settings, PsiFile psiFile, Document document, Project project,
                             final Window outerPanel, int cursorOffset)
  {
    this.settings = settings;
    this.document = document;
    this.psiFile = psiFile;
    createFilePopupEntry(psiFile);
    this.project = project;
    this.outerPanel = outerPanel;
    this.cursorOffset = cursorOffset;
    try {
      SwingUtilities.invokeAndWait(
        new Runnable() {
          public void run() {
//                            popup = new JWindow();
            JPanel tempPanel = new JPanel(new GridBagLayout());
            JLabel label = new JLabel("Live Rearranger parsing file...");
            Border b = BorderFactory.createRaisedBevelBorder();
            tempPanel.setBorder(b);
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(5, 5, 5, 5);
            tempPanel.add(label, constraints);
            Dimension d = outerPanel.getSize();
            Dimension c = tempPanel.getPreferredSize();
            int x = (d.width - c.width) / 2;
            int y = (d.height - c.height) / 2;
            if (x < 0) x = 0;
            if (y < 0) y = 0;
            popup = PopupFactory.getSharedInstance().getPopup(outerPanel, tempPanel, x, y);
//                            popup.getContentPane().add(tempPanel);
//                            popup.setLocation(x, y);
            LOG.debug("initial outerPanel size=" + d + ", tempPanel preferred size=" + c);
            LOG.debug("Constructing initial Popup at x,y=" + x + "," + y);
//                            popup.pack();
//                            popup.setVisible(true);
//                            popup.requestFocusInWindow();
            popup.show();
            Cursor cu = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR);
            oldCursor = outerPanel.getCursor();
            LOG.debug("setCursor (WAIT)" + cu + " on " + outerPanel);
            outerPanel.setCursor(cu);
          }
        }
      );
    }
    catch (InterruptedException ie) {
      throw new RuntimeException(ie);
    }
    catch (InvocationTargetException ite) {
      throw new RuntimeException(ite);
    }
  }

  public void setRearrangementOccurred(boolean rearrangementOccurred) {
    this.rearrangementOccurred = rearrangementOccurred;
  }

  public void setResultRuleInstances(List<RuleInstance> resultRuleInstances) {
    this.myResultRuleInstances = resultRuleInstances;
    treeComponent = new PopupTreeComponent(settings, resultRuleInstances, myPsiFileEntry);
  }

  /** Display a live rearrangement window. */
  public void liveRearranger() {
    try {
      SwingUtilities.invokeAndWait(
        new Runnable() {
          public void run() {
            containerPanel = getContainerPanel();
            LOG.debug("containerPanel.isFocusable=" + containerPanel.isFocusable());
            Border b = BorderFactory.createRaisedBevelBorder();
            containerPanel.setBorder(b);
            Dimension d = outerPanel.getSize();
            Dimension c = containerPanel.getPreferredSize();
            int x = (d.width - c.width) / 2;
            int y = (d.height - c.height) / 2;
            if (x < 0) x = 0;
            if (y < 0) y = 0;
            popup.hide(); // destroy initial (interim) popup
            popup = PopupFactory.getSharedInstance().getPopup(outerPanel, containerPanel, x, y);
//        popup = new JFrame();
//                            final Container contentPane = popup.getContentPane();
//                            contentPane.remove(0);
//                            contentPane.add(containerPanel);
//                            contentPane.validate();
//                            popup.setLocation(x, y);
            LOG.debug("setCursor (original)" + oldCursor + " on " + outerPanel);
            outerPanel.setCursor(oldCursor);
//                            logger.debug("set default cursor on " + popup);
//                            popup.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
//                            popup.repaint();
            LOG.debug("outerPanel size=" + d + ", containerPanel preferred size=" + c);
            LOG.debug("Constructing Popup at x,y=" + x + "," + y);
            LiveRearrangerActionHandler.setInProgress(true);

//                            popup.addWindowFocusListener(windowFocusListener);
//                            popup.addWindowListener(windowAdapter);
//                            popup.addMouseListener(mouseAdapter);
//                            popup.addFocusListener(focusAdapter);
//                            contentPane.addFocusListener(focusAdapter);
            outerPanel.addWindowFocusListener(
              windowFocusListener = getWindowFocusListener("outerPanel")
            );
            outerPanel.addWindowListener(windowAdapter = getWindowAdapter("outerPanel"));
            outerPanel.addMouseListener(mouseAdapter = getMouseAdapter("outerPanel"));
            ma2 = getMouseAdapter("focus owner");
            outerPanelFocusOwner = outerPanel.getFocusOwner();
            if (outerPanelFocusOwner != null) {
              outerPanelFocusOwner.addMouseListener(ma2);
            }
            outerPanel.addFocusListener(focusAdapter = getFocusAdapter("outerPanel"));
            containerPanel.addFocusListener(getFocusAdapter("containerPanel"));

            keyEventDispatcher = new KeyEventDispatcher() {
              public boolean dispatchKeyEvent(KeyEvent e) {
                LOG.debug(
                  "key event dispatcher: outerPanel isFocused=" +
                  outerPanel.isFocused() +
                  ", rearrangementOccurred=" +
                  rearrangementOccurred +
                  " keyEvent=" +
                  e
                );
                if (e.getID() == KeyEvent.KEY_RELEASED &&
                    (rearrangementOccurred || sawKeyPressed))
                {
                  LOG.debug("key event dispatcher: disposing popup, saw keyEvent " + e);
                  finish();
                }
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                  sawKeyPressed = true;
                }
                return true;
              }
            };
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(
              keyEventDispatcher
            );
//        wasLightWeight = ToolTipManager.sharedInstance().isLightWeightPopupEnabled();
//        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
            popup.show();
//        popup.setVisible(true);
          }
        }
      );
    }
    catch (InterruptedException ie) {
      throw new RuntimeException(ie);
    }
    catch (InvocationTargetException ite) {
      throw new RuntimeException(ite);
    }
    LOG.debug("exit liveRearranger");
  }

  private MouseAdapter getMouseAdapter(final String component) {
    return new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        LOG.debug(
          component +
          ": mouse clicked at " +
          e.getX() +
          "," +
          e.getY() +
          "; window size/position is " +
          outerPanel.getBounds()
        );
        LOG.debug("close popup and finish");
        finish();
        super.mouseClicked(e);
      }
    };
  }

  private FocusAdapter getFocusAdapter(final String component) {
    return new FocusAdapter() {
      public void focusGained(FocusEvent e) {
        LOG.debug(component + ": focus gained");
      }

      public void focusLost(FocusEvent e) {
        LOG.debug(component + ": focus lost, close popup & finish");
        finish();
      }
    };
  }

  private WindowAdapter getWindowAdapter(final String component) {
    return new WindowAdapter() {
      public void windowLostFocus(WindowEvent e) {
        LOG.debug(component + ": lost focus");
        LOG.debug("close popup and finish");
        finish();
      }

      public void windowClosing(WindowEvent e) {
        LOG.debug(component + ": window closing");
      }
    };
  }

  private WindowFocusListener getWindowFocusListener(final String component) {
    return new WindowFocusListener() {
      public void windowGainedFocus(WindowEvent e) {
        LOG.debug(component + ": gained window focus");
      }

      public void windowLostFocus(WindowEvent e) {
        LOG.debug(component + ": lost window focus");
        LOG.debug("close popup and finish");
        finish();
      }
    };
  }

  public void finish() {
    LOG.debug("entered finish() on thread " + Thread.currentThread().getName());
//        popup.setVisible(false);  
//        popup.dispose();
//        popup.removeWindowFocusListener(windowFocusListener);
//        popup.removeWindowListener(windowAdapter);
//        popup.removeMouseListener(mouseAdapter);
//        popup.removeFocusListener(focusAdapter);
//        popup.getContentPane().removeFocusListener(focusAdapter);
    outerPanel.removeWindowFocusListener(windowFocusListener);
    if (outerPanelFocusOwner != null) {
      outerPanelFocusOwner.removeMouseListener(ma2);
    }
    outerPanel.removeWindowListener(windowAdapter);
    outerPanel.removeMouseListener(mouseAdapter);
    outerPanel.removeFocusListener(focusAdapter);
//        containerPanel.removeFocusListener(containerPanel.getFocusListeners()[0]);
    popup.hide();
//        popup.dispose();

    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyEventDispatcher);
//        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(wasLightWeight);
    if (!rearrangementOccurred) {
      LOG.debug("no rearrangement occurred, not rearranging document");
      LiveRearrangerActionHandler.setInProgress(false);
      return;
    }
    LOG.debug("rearranging document");
    final Runnable task = new Runnable() {
      public void run() {
        if (document != null) {
          final Emitter e = new Emitter(psiFile, myResultRuleInstances, document);
          e.emitRearrangedDocument();
        }
      }
    };
    final Application application = ApplicationManager.getApplication();

    application.runWriteAction(
      new Runnable() {
        public void run() {
          CommandProcessor.getInstance().executeCommand(project, task, "Rearrange", null);
        }
      }
    );

    LiveRearrangerActionHandler.setInProgress(false);
    LOG.debug("exit finish() on thread " + Thread.currentThread().getName());
  }

  private JPanel getContainerPanel() {
    final JPanel containerPanel = new JPanel(new GridBagLayout());
    Border etched = BorderFactory.createTitledBorder(
      BorderFactory.createEtchedBorder(), "Live Rearranger", TitledBorder.CENTER,
      TitledBorder.TOP
    );
    containerPanel.setBorder(etched);
    final GridBagConstraints scrollPaneConstraints = new GridBagConstraints();
    scrollPaneConstraints.insets = new Insets(3, 3, 3, 3);
    scrollPaneConstraints.fill = GridBagConstraints.BOTH;
    scrollPaneConstraints.gridwidth = GridBagConstraints.REMAINDER;
    scrollPaneConstraints.gridheight = GridBagConstraints.REMAINDER;
    scrollPaneConstraints.weightx = 1;
    scrollPaneConstraints.weighty = 1;
    scrollPaneConstraints.gridx = 0;
    scrollPaneConstraints.gridy = 1;

    final JScrollPane treeView = getScrollPane();
    final JComponent showTypesBox =
      new IconBox(containerPanel, scrollPaneConstraints, this) {
        boolean getSetting() {
          return settings.isShowParameterTypes();
        }

        void setSetting(boolean value) {
          settings.setShowParameterTypes(value);
        }

        Icon getIcon() {
          return IconUtil.getIcon("ShowParamTypes");
        }

        String getToolTipText() {
          return "Show parameter types";
        }

        int getShortcut() {
          return KeyEvent.VK_T;
        }
      }.getIconBox();
    final JComponent showNamesBox =
      new IconBox(containerPanel, scrollPaneConstraints, this) {
        boolean getSetting() {
          return settings.isShowParameterNames();
        }

        void setSetting(boolean value) {
          settings.setShowParameterNames(value);
        }

        Icon getIcon() {
          return IconUtil.getIcon("ShowParamNames");
        }

        String getToolTipText() {
          return "Show parameter names";
        }

        int getShortcut() {
          return KeyEvent.VK_N;
        }
      }.getIconBox();
    final JComponent showFieldsBox =
      new IconBox(containerPanel, scrollPaneConstraints, this) {
        boolean getSetting() {
          return settings.isShowFields();
        }

        void setSetting(boolean value) {
          settings.setShowFields(value);
        }

        Icon getIcon() {
          return IconUtil.getIcon("ShowFields");
        }

        String getToolTipText() {
          return "Show fields";
        }

        int getShortcut() {
          return KeyEvent.VK_F;
        }
      }.getIconBox();
    final JComponent showTypeAfterMethodBox =
      new IconBox(containerPanel, scrollPaneConstraints, this) {
        boolean getSetting() {
          return settings.isShowTypeAfterMethod();
        }

        void setSetting(boolean value) {
          settings.setShowTypeAfterMethod(value);
        }

        Icon getIcon() {
          return IconUtil.getIcon("ShowTypeAfterMethod");
        }

        String getToolTipText() {
          return "Show type after method";
        }

        int getShortcut() {
          return KeyEvent.VK_A;
        }
      }.getIconBox();
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.weightx = 0;
    constraints.weighty = 0;
    constraints.insets = new Insets(5, 5, 5, 5);
    containerPanel.add(showTypesBox, constraints);
    constraints.gridx++;
    containerPanel.add(showNamesBox, constraints);
    constraints.gridx++;
    containerPanel.add(showFieldsBox, constraints);
    constraints.gridx++;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    containerPanel.add(showTypeAfterMethodBox, constraints);
    containerPanel.add(treeView, scrollPaneConstraints);
    return containerPanel;
  }

  /**
   * build a JTree containing classes, fields and methods, in accordance with settings.
   *
   * @return
   */
  public JScrollPane getScrollPane() {
    // Create the nodes.
    PopupTree tree = treeComponent.createLiveRearrangerTree();

    /** only expand node where cursor is located.  Inspect all rows; deepest node that covers
     * cursor location is the best to expand.  (Parent node like a class contains a method where
     * the cursor is; we want to expand the method, not just the class.
     */
    int expandRow = -1;
    for (int i = 0; i < tree.getRowCount(); i++) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)tree.getPathForRow(i).getLastPathComponent();
      if (node.getUserObject() instanceof RangeEntry) {
        RangeEntry re = (RangeEntry)node.getUserObject();
        if (re.getStart().getTextRange().getStartOffset() <= cursorOffset &&
            re.getEnd().getTextRange().getEndOffset() >= cursorOffset)
        {
          LOG.debug(
            "node " +
            i +
            " contained cursor (offset=" +
            cursorOffset +
            "): " + re
          );
          expandRow = i;
        }
      }
      else {
        LOG.debug("expand node candidate not RangeEntry; node=" + node);
      }
    }
    if (expandRow >= 0) {
      LOG.debug("expand row " + expandRow);
      tree.expandRow(expandRow);
    }
    JScrollPane treeView = new JScrollPane(tree);
    treeView.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    treeView.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    Dimension d = treeView.getPreferredSize();
    if (d.width < 400) d.width = 400;
    if (d.height < 300) d.height = 300;
    treeView.setPreferredSize(d);
    tdt = new TreeDropTarget(tree, this);
    tds = new TreeDragSource(tree, DnDConstants.ACTION_MOVE, tdt);
    return treeView;
  }

  //    class RearrangerTest
//            extends LightCodeInsightTestCase
//    {
//        private RearrangerSettings rs;
//
//        protected final void setUp() throws Exception
//        {
//            super.setUp();
//        }
//
//
//
  public static void main(String[] args) {
//        RearrangerTest t = new RearrangerTest();
//        t.testIt();
//        void testIt()
//        {
    final RearrangerSettings settings = new RearrangerSettings();
    FilePopupEntry pf = new FilePopupEntry() {
      public String getTypeIconName() {
        return "nodes/ppFile";
      }

      public String[] getAdditionalIconNames() {
        return null;
      }

      public JLabel getPopupEntryText(RearrangerSettings settings) {
        return new JLabel("FileName.java");
      }
    };
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
    catch (Exception e) {
    }

//            logger.setAdditivity(false);
//            logger.addAppender(new ConsoleAppender(new PatternLayout("[%7r] %6p - %30.30c - %m \n")));
//            logger.setLevel(Level.DEBUG);
//            logger.debug("Testing LiveRearrangerPopup");

    final JFrame frame = new JFrame("SwingApplication");
//        Window window = new Window(frame);
    JPanel panel = new JPanel();
    panel.setSize(800, 600);
    frame.setSize(800, 600);
    frame.getRootPane().add(panel);
//        window.setSize(800, 600);
//        window.getRootPane().add(panel);
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = 1.0d;
    constraints.weighty = 1.0d;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.gridx = 0;
    constraints.gridy = 0;
    LiveRearrangerPopup lrp = new LiveRearrangerPopup(
      // getProject(),
      settings, pf, frame, null,
      null
    );
//        final JPanel object = lrp.getContainerPanel();
//        frame.getContentPane().setLayout(new GridBagLayout());
//        frame.getContentPane().add(object, constraints);

    //Finish setting up the frame, and show it.
    frame.addWindowListener(
      new WindowAdapter() {
        public void windowClosing(final WindowEvent e) {
          System.exit(0);
        }
      }
    );
//        frame.pack();
    frame.setVisible(true);
    lrp.liveRearranger();
  }
}

//
//}
