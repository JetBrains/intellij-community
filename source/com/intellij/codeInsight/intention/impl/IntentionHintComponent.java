package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListPopup;
import com.intellij.ui.ListenerUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.Iterator;

/**
 * @author Mike
 * @author Valentin
 * @author Eugene Belyaev
 */
public class IntentionHintComponent extends JPanel {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.codeInsight.intention.impl.IntentionHintComponent.ListPopupRunnable");

  private static final Icon ourIntentionIcon = IconLoader.getIcon("/actions/intentionBulb.png");
  private static final Icon ourQuickFixIcon = IconLoader.getIcon("/actions/quickfixBulb.png");;
  private static final Icon ourIntentionOffIcon = IconLoader.getIcon("/actions/intentionOffBulb.png");
  private static final Icon ourQuickFixOffIcon = IconLoader.getIcon("/actions/quickfixOffBulb.png");
  private static final Icon ourArrowIcon = IconLoader.getIcon("/general/arrowDown.png");
  private static final Border INACTIVE_BORDER = null;
  private static final Insets INACTIVE_MARGIN = new Insets(0, 0, 0, 0);
  private static final Insets ACTIVE_MARGIN = new Insets(0, 0, 0, 0);

  private final Project myProject;
  private final Editor myEditor;

  private static Alarm myAlarm = new Alarm();

  private JList myList;
  private RowIcon myHighlightedIcon;
  private JButton myButton;

  private Icon myBackgroundIcon;
  private final Icon mySmartTagIcon;

  private static final int DELAY = 500;
  private MyComponentHint myComponentHint;
  private static final Color BACKGROUND_COLOR = new Color(255, 255, 255, 0);
  private ListPopup myListPopup;
  private boolean myPopupShown = false;
  private ArrayList myQuickFixes;

  private class IntentionActionWithTextCaching {
    private IntentionAction myAction;
    private String myText = null;

    public IntentionActionWithTextCaching(IntentionAction action) {
      myAction = action;
      myText = myAction.getText();
    }

    String getText() {
      return myText;
    }

    public IntentionAction getAction() {
      return myAction;
    }
  }

  private IntentionActionWithTextCaching[] wrapActions(IntentionAction[] actions) {
    IntentionActionWithTextCaching[] wrapped = new IntentionActionWithTextCaching[actions.length];
    for (int i = 0; i < actions.length; i++) {
      wrapped[i] = new IntentionActionWithTextCaching(actions[i]);
    }

    return wrapped;
  }

  public static IntentionHintComponent showIntentionHint(Project project,
                                                         Editor view,
                                                         ArrayList intentions,
                                                         ArrayList quickFixes,
                                                         boolean showExpanded) {
    final IntentionHintComponent component = new IntentionHintComponent(project, view, intentions, quickFixes);

    if (showExpanded) {
      component.showIntentionHintImpl(false);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            component.showPopup();
          }
        });
    }
    else {
      component.showIntentionHintImpl(true);
    }

    return component;
  }

  public void updateIfNotShowingPopup(ArrayList quickfixes, ArrayList intentions) {
    if (myListPopup == null) {
      initList(quickfixes, intentions);
    }
  }

  private void showIntentionHintImpl(final boolean delay) {
    final int offset = myEditor.getCaretModel().getOffset();
    final HintManager hintManager = HintManager.getInstance();

    myComponentHint.setShouldDelay(delay);

    hintManager.showQuestionHint(myEditor,
                                 getHintPosition(myEditor, offset),
                                 offset,
                                 offset,
                                 myComponentHint,
                                 new QuestionAction() {
                                   public boolean execute() {
                                     showPopup();
                                     return true;
                                   }
                                 });
  }

  private static Point getHintPosition(Editor editor, int offset) {
    final LogicalPosition pos = editor.offsetToLogicalPosition(offset);
//    Dimension hintSize = internalComponent.getPreferredSize();
    int line = pos.line;


    Point location;
    final Point position = editor.logicalPositionToXY(new LogicalPosition(line, 0));
    final int yShift = (ourIntentionIcon.getIconHeight() - editor.getLineHeight() - 1) / 2 - 1;

    LOG.assertTrue(editor.getComponent().isDisplayable());
    location = SwingUtilities.convertPoint(editor.getContentComponent(),
                                           new Point(editor.getScrollingModel().getVisibleArea().x,
                                                     position.y + yShift),
                                           editor.getComponent().getRootPane().getLayeredPane());

    return new Point(location.x, location.y);
  }

  public IntentionHintComponent(Project project, Editor editor, ArrayList intentions, ArrayList quickFixes) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myProject = project;
    myEditor = editor;

    setLayout(new BorderLayout());
    setOpaque(false);

    boolean showFix = false;
    for (Iterator iterator = quickFixes.iterator(); iterator.hasNext();) {
      IntentionAction fix = (IntentionAction)iterator.next();
      if (IntentionManagerSettings.getInstance().isShowLightBulb(fix)) {
        showFix = true;
        break;
      }
    }
    mySmartTagIcon = showFix ? ourQuickFixIcon : ourIntentionIcon;

    myHighlightedIcon = new RowIcon(2);
    myHighlightedIcon.setIcon(mySmartTagIcon, 0);
    myHighlightedIcon.setIcon(ourArrowIcon, 1);

    initList(quickFixes, intentions);

    myButton = new JButton(mySmartTagIcon);
    myButton.setFocusable(false);
    myButton.setMargin(INACTIVE_MARGIN);
//    myButton.setBackground(BACKGROUND_COLOR);
    myButton.setBorderPainted(false);
    myButton.setContentAreaFilled(false);

    add(myButton, BorderLayout.CENTER);
    setBorder(INACTIVE_BORDER);

    myButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        showPopup();
      }
    });

    myButton.addMouseListener(new MouseAdapter() {
      public void mouseEntered(MouseEvent e) {
        onMouseEnter();
      }

      public void mouseExited(MouseEvent e) {
        onMouseExit();
      }
    });

    myComponentHint = new MyComponentHint(this);
  }

  private void initList(ArrayList quickFixes, ArrayList intentions) {
    ArrayList actions = (ArrayList)quickFixes.clone();
    actions.addAll(intentions);
    myList = new MyList(wrapActions((IntentionAction[])actions.toArray(new IntentionAction[actions.size()])));
    myList.setCellRenderer(new PopupCellRenderer(myList));
    myList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myQuickFixes = quickFixes;
  }

  private void onMouseExit() {
    Window ancestor = SwingUtilities.getWindowAncestor(myList);
    if (ancestor == null) {
      myButton.setBackground(BACKGROUND_COLOR);
      myButton.setIcon(mySmartTagIcon);
      setBorder(INACTIVE_BORDER);
      myButton.setMargin(INACTIVE_MARGIN);
      updateComponentHintSize();
    }
  }

  private void onMouseEnter() {
    myButton.setBackground(HintUtil.QUESTION_COLOR);
    myButton.setIcon(myHighlightedIcon);
    setBorder(BorderFactory.createLineBorder(Color.black));
    myButton.setMargin(ACTIVE_MARGIN);
    updateComponentHintSize();

    String acceleratorsText = KeymapUtil.getFirstKeyboardShortcutText(
      ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS));
    if (acceleratorsText.length() > 0) {
      myButton.setToolTipText("Click or press " + acceleratorsText);
    }
  }

  private void updateBackgroundImage(JComponent parentComponent, int x, int y) {
    Dimension size = getPreferredSize();
    size.width += 2;
    size.height += 2;
    BufferedImage bufferedImage = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);

    JLayeredPane layeredPane = parentComponent.getRootPane().getLayeredPane();
    Point layeredPanePoint = SwingUtilities.convertPoint(parentComponent, x, y, layeredPane);

    Graphics2D graphics = bufferedImage.createGraphics();
    graphics.setClip(0, 0, size.width, size.height);
    graphics.translate(-layeredPanePoint.x, -layeredPanePoint.y);

    layeredPane.paint(graphics);

    myBackgroundIcon = new ImageIcon(bufferedImage);
  }

  private void updateComponentHintSize() {
    Component component = myComponentHint.getComponent();
    component.setSize(getPreferredSize().width, getHeight());
  }

  public void closePopup() {
    if (myPopupShown) {
      myListPopup.closePopup(false);
      myPopupShown = false;
    }
  }

  public void showPopup() {
    myListPopup = new ListPopup(null, myList, new ListPopupRunnable(), myProject);

    final Component component = myComponentHint.getComponent();
    final EventListener[] listeners = component.getListeners(FocusListener.class);
    for (int i = 0; i < listeners.length; i++) {
      EventListener listener = listeners[i];
      ListenerUtil.addFocusListener(myListPopup.getWindow(), (FocusListener)listener);
    }

    myListPopup.getWindow().addWindowListener(new WindowAdapter() {
      public void windowClosed(WindowEvent e) {
        HintManager.getInstance().hideAllHints();
      }
    });

    showIntentionHintImpl(false);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final Point p = new Point(0, getHeight());
        SwingUtilities.convertPointToScreen(p, IntentionHintComponent.this);
        myListPopup.show(p.x, p.y);
        myPopupShown = true;
        onMouseEnter();
      }
    });
  }

  public void paint(Graphics g) {
    myBackgroundIcon.paintIcon(null, g, 0, 0);
    super.paint(g);
  }

  private class MyComponentHint extends LightweightHint {
    private boolean myVisible = false;
    private boolean myShouldDelay;

    public MyComponentHint(JComponent component) {
      super(component);
    }

    public void show(final JComponent parentComponent, final int x, final int y, final JComponent focusBackComponent) {
      myVisible = true;
      if (myShouldDelay) {
        myAlarm.cancelAllRequests();
        myAlarm.addRequest(new Runnable() {
          public void run() {
            showImpl(parentComponent, x, y, focusBackComponent);
          }
        }, DELAY);
      }
      else {
        showImpl(parentComponent, x, y, focusBackComponent);
      }
    }

    private void showImpl(JComponent parentComponent, int x, int y, JComponent focusBackComponent) {
      if (!parentComponent.isShowing()) return;
      updateBackgroundImage(parentComponent, x, y);
      super.show(parentComponent, x, y, focusBackComponent);
    }

    public void hide() {
      myVisible = false;
      myAlarm.cancelAllRequests();
      super.hide();
    }

    public boolean isVisible() {
      return myVisible || super.isVisible();
    }

    public void setShouldDelay(boolean shouldDelay) {
      myShouldDelay = shouldDelay;
    }
  }

  private class ListPopupRunnable implements Runnable {
    public void run() {
      if (myList.getSelectedIndex() < 0) return;
      final IntentionAction action = ((IntentionActionWithTextCaching)myList.getSelectedValue()).getAction();

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          HintManager hintManager = HintManager.getInstance();
          hintManager.hideAllHints();
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
              PsiDocumentManager.getInstance(myProject).commitAllDocuments();

              if (action.isAvailable(myProject, myEditor, file)) {
                Runnable runnable = new Runnable() {
                  public void run() {
                    try {
                      action.invoke(myProject, myEditor, file);
                      DaemonCodeAnalyzer.getInstance(myProject).updateVisibleHighlighters(myEditor);
                    }
                    catch (IncorrectOperationException e1) {
                      LOG.error(e1);
                    }
                  }
                };

                if (action.startInWriteAction()) {
                  final Runnable _runnable = runnable;
                  runnable = new Runnable() {
                    public void run() {
                      ApplicationManager.getApplication().runWriteAction(_runnable);
                    }
                  };
                }

                CommandProcessor.getInstance().executeCommand(myProject, runnable, action.getText(), null);
              }
            }
          });
        }
      });
    }
  }

  private class PopupCellRenderer implements ListCellRenderer {
    private final JList myList;
    private final JPanel myPanel = new JPanel();
    private final JLabel myLabel = new JLabel();
    private final JButton myButton = new JButton();
    private Point myLastMousePoint = null;
    private int myLastItemIndex = -1;
    private boolean myMousePressed = false;
    private final IntentionManagerSettings mySettings;

    public PopupCellRenderer(JList list) {
      myList = list;
      myPanel.setLayout(new BorderLayout());
      myPanel.add(myButton, BorderLayout.WEST);
      myPanel.add(myLabel, BorderLayout.CENTER);
      myButton.setIcon(mySmartTagIcon);
      myButton.setMargin(new Insets(0, 0, 0, 0));
      myButton.setContentAreaFilled(false);

      myLabel.setOpaque(true);
//      myButton.setOpaque(true);
      mySettings = IntentionManagerSettings.getInstance();

      myButton.setBorderPainted(false);
      myList.addMouseMotionListener(new MouseMotionListener() {
        public void mouseDragged(MouseEvent e) {
          updateLastPoint(e);
        }

        public void mouseMoved(MouseEvent e) {
          updateLastPoint(e);
        }
      });
      myList.addMouseListener(new MouseAdapter() {
        public void mouseEntered(MouseEvent e) {
          updateLastPoint(e);
        }

        public void mouseExited(MouseEvent e) {
          myLastMousePoint = null;
          myLastItemIndex = -1;
          myList.repaint();
        }

        public void mousePressed(MouseEvent e) {
          e.consume();
          myMousePressed = true;
          myList.repaint();
        }

        public void mouseClicked(MouseEvent e) {
          Point point = e.getPoint();
          if (isInButton(point)) {
            int index = myList.locationToIndex(point);
            IntentionAction action = ((IntentionActionWithTextCaching)myList.getModel().getElementAt(index)).getAction();
            mySettings.setShowLightBulb(action, !mySettings.isShowLightBulb(action));
            e.consume();
            myList.repaint();
          }
        }

        public void mouseReleased(MouseEvent e) {
          e.consume();
          myMousePressed = false;
          myList.repaint();
        }
      });
    }

    public boolean isInButton(Point point) {
      int x = point.x - myList.getInsets().left;
      boolean isInButton = x <= myButton.getPreferredSize().width;
      return isInButton;
    }

    private void updateLastPoint(MouseEvent e) {
      myLastMousePoint = e.getPoint();
      myLastItemIndex = myList.locationToIndex(myLastMousePoint);
      myList.repaint();
    }

    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      IntentionActionWithTextCaching wrapped = (IntentionActionWithTextCaching)value;
      IntentionAction action = wrapped.getAction();

      myLabel.setText(" " + wrapped.getText() + " ");
      myLabel.setFont(list.getFont());

      Color bg;
      Color fg;

      if (!isSelected) {
        fg = list.getForeground();
        bg = list.getBackground();
      }
      else {
        fg = list.getSelectionForeground();
        bg = list.getSelectionBackground();
      }


      myLabel.setForeground(fg);
      myLabel.setBackground(bg);
      myButton.setBackground(list.getBackground());
      myPanel.setBackground(list.getBackground());

      if (mySettings.isShowLightBulb(action)) {
        if (myQuickFixes.contains(action)) {
          myButton.setIcon(ourQuickFixIcon);
        }
        else {
          myButton.setIcon(ourIntentionIcon);
        }
      }
      else {
        if (myQuickFixes.contains(action)) {
          myButton.setIcon(ourQuickFixOffIcon);
        }
        else {
          myButton.setIcon(ourIntentionOffIcon);
        }
      }

      myButton.setBorderPainted(false);
      myButton.getModel().setArmed(false);
      myButton.getModel().setPressed(false);

      if (myLastItemIndex == index) {
        int x = myLastMousePoint.x - myList.getInsets().left;

        if (x <= myButton.getPreferredSize().width) {
          myButton.getModel().setArmed(myMousePressed);
          myButton.getModel().setPressed(myMousePressed);
          myButton.setBorderPainted(true);
        }
      }

      return myPanel;
    }
  }

  private class MyList extends JList {
    public MyList(IntentionActionWithTextCaching[] actions) {
      super(actions);

      registerKeyboardAction(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final IntentionManagerSettings settings = IntentionManagerSettings.getInstance();
          IntentionAction action = ((IntentionActionWithTextCaching)getSelectedValue()).getAction();
          if (action != null) {
            settings.setShowLightBulb(action, !settings.isShowLightBulb(action));
          }
          repaint();
        }
      }, KeyStroke.getKeyStroke(' '), WHEN_FOCUSED);
    }

    public String getToolTipText(MouseEvent event) {
      PopupCellRenderer cellRenderer = (PopupCellRenderer)getCellRenderer();
      if (cellRenderer.isInButton(event.getPoint())) {
        IntentionAction action = ((IntentionActionWithTextCaching)getModel().getElementAt(
          locationToIndex(event.getPoint()))).getAction();
        if (IntentionManagerSettings.getInstance().isShowLightBulb(action)) {
          return "Don't show \"lightbulb\" for '" + action.getFamilyName() + "' action";
        }
        else {
          return "Show \"lightbulb\" for '" + action.getFamilyName() + "' action";
        }
      }
      return null;
    }

    public Point getToolTipLocation(MouseEvent event) {
      PopupCellRenderer cellRenderer = (PopupCellRenderer)getCellRenderer();
      if (cellRenderer.isInButton(event.getPoint())) {
        return new Point(event.getPoint().x + 5, event.getPoint().y - 20);
      }
      return super.getToolTipLocation(event);
    }
  }
}
