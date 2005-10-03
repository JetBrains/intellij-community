package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.EmptyIntentionAction;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionManagerSettings;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.impl.EmptyIcon;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListPopupWithChildPopups;
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
import java.util.List;

/**
 * @author Mike
 * @author Valentin
 * @author Eugene Belyaev
 */
public class IntentionHintComponent extends JPanel {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.codeInsight.intention.impl.IntentionHintComponent.ListPopupRunnable");

  private static final Icon ourIntentionIcon = IconLoader.getIcon("/actions/intentionBulb.png");
  private static final Icon ourQuickFixIcon = IconLoader.getIcon("/actions/quickfixBulb.png");
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
  private ListPopupWithChildPopups myListPopup;
  private ListPopupWithChildPopups myParentListPopup;
  private boolean myPopupShown = false;
  private List<IntentionAction> myQuickFixes;

  private static class IntentionActionWithTextCaching {
    private ArrayList<IntentionAction> myOptionIntentions;
    private ArrayList<IntentionAction> myOptionFixes;
    private String myText = null;
    private IntentionAction myAction;

    public IntentionActionWithTextCaching(IntentionAction action) {
      myOptionIntentions = new ArrayList<IntentionAction>();
      myOptionFixes = new ArrayList<IntentionAction>();
      myText = action.getText();
      myAction = action;
    }

    String getText() {
      return myText;
    }

    public void addAction(final IntentionAction action, boolean isFix) {
      if (isFix) {
        myOptionFixes.add(action);
      }
      else {
        myOptionIntentions.add(action);
      }
    }

    public IntentionAction getAction() {
      return myAction;
    }

    public List<IntentionAction> getOptionIntentions() {
      return myOptionIntentions;
    }

    public List<IntentionAction> getOptionFixes() {
      return myOptionFixes;
    }
  }

  private IntentionActionWithTextCaching[] wrapActions(List<Pair<IntentionAction, List<IntentionAction>>> actions) {
    IntentionActionWithTextCaching [] compositeActions = new IntentionActionWithTextCaching[actions.size()];
    int index = 0;
    for (Pair<IntentionAction, List<IntentionAction>> pair : actions) {
      if (pair.first != null) {
        IntentionActionWithTextCaching action = new IntentionActionWithTextCaching(pair.first);
        if (pair.second != null) {
          for (IntentionAction intentionAction : pair.second) {
            action.addAction(intentionAction, myQuickFixes.contains(intentionAction));
          }
        }
        compositeActions[index ++] = action;
      }
    }
    return compositeActions;
  }

  public static IntentionHintComponent showIntentionHint(Project project,
                                                         Editor view,
                                                         ArrayList<Pair<IntentionAction, List<IntentionAction>>> intentions,
                                                         ArrayList<Pair<IntentionAction, List<IntentionAction>>> quickFixes,
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

  public void updateIfNotShowingPopup(ArrayList<Pair<IntentionAction, List<IntentionAction>>> quickfixes,
                                      ArrayList<Pair<IntentionAction, List<IntentionAction>>> intentions) {
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

  private IntentionHintComponent(Project project,
                                 Editor editor,
                                 ArrayList<Pair<IntentionAction, List<IntentionAction>>> intentions,
                                 ArrayList<Pair<IntentionAction, List<IntentionAction>>> quickFixes,
                                 ListPopupWithChildPopups parentPopup) {
    this(project, editor, intentions, quickFixes);
    myParentListPopup = parentPopup;
  }

  public IntentionHintComponent(Project project,
                                Editor editor,
                                ArrayList<Pair<IntentionAction, List<IntentionAction>>> intentions,
                                ArrayList<Pair<IntentionAction, List<IntentionAction>>> quickFixes) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myProject = project;
    myEditor = editor;

    setLayout(new BorderLayout());
    setOpaque(false);

    boolean showFix = false;
    for (final Pair<IntentionAction, List<IntentionAction>> pairs : quickFixes) {
      IntentionAction fix = pairs.first;
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

  private void initList(ArrayList<Pair<IntentionAction, List<IntentionAction>>> quickFixes,
                        ArrayList<Pair<IntentionAction, List<IntentionAction>>> intentions) {
    List<Pair<IntentionAction, List<IntentionAction>>> allActions = new ArrayList<Pair<IntentionAction, List<IntentionAction>>>(quickFixes);
    allActions.addAll(intentions);
    List<IntentionAction> actions = new ArrayList<IntentionAction>();
    for (Pair<IntentionAction, List<IntentionAction>> pair : quickFixes) {
      actions.add(pair.first);
      if (pair.second != null) {
        actions.addAll(pair.second);
      }
    }
    myQuickFixes = actions;

    myList = new MyList(wrapActions(allActions));
    myList.setCellRenderer(new PopupCellRenderer(myList));
    myList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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
      myButton.setToolTipText(CodeInsightBundle.message("lightbulb.tooltip", acceleratorsText));
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
    myListPopup = new ListPopupWithChildPopups(null, myList, myProject, myParentListPopup, new ListPopupRunnable(), new Runnable(){
      public void run() {
        showIntentionHintImpl(false);
      }
    });

    final Component component = myComponentHint.getComponent();
    final EventListener[] listeners = component.getListeners(FocusListener.class);
    for (EventListener listener : listeners) {
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
        Point p;
        if (myParentListPopup != null) {
          p = new Point(myParentListPopup.getWindow().getWidth(), myParentListPopup.getSelectedHeight());
          SwingUtilities.convertPointToScreen(p, myParentListPopup.getWindow());
        }
        else {
          p = new Point(0, getHeight());
          SwingUtilities.convertPointToScreen(p, IntentionHintComponent.this);
        }
        myListPopup.show(p.x, p.y, new Runnable() {
          public void run() {
            final IntentionActionWithTextCaching action = (IntentionActionWithTextCaching)myList.getSelectedValue();
            if (action.getOptionIntentions().size() + action.getOptionFixes().size() > 0){
              openChildPopup(action);
            } else {
              showIntentionHintImpl(false);
            }
          }
        }, new Computable<Boolean>(){
          public Boolean compute() {
            final IntentionActionWithTextCaching action = (IntentionActionWithTextCaching)myList.getSelectedValue();
            return new Boolean(action.getAction() instanceof EmptyIntentionAction && action.getOptionIntentions().size() + action.getOptionFixes().size() > 0);
          }
        });
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

  private void openChildPopup(final IntentionActionWithTextCaching action) {
    final ArrayList<Pair<IntentionAction, List<IntentionAction>>> intentions = new ArrayList<Pair<IntentionAction, List<IntentionAction>>>();
    final List<IntentionAction> optionIntentions = action.getOptionIntentions();
    for (final IntentionAction optionIntention : optionIntentions) {
      intentions.add(new Pair<IntentionAction, List<IntentionAction>>(optionIntention, null));
    }
    final ArrayList<Pair<IntentionAction, List<IntentionAction>>> quickFixes = new ArrayList<Pair<IntentionAction, List<IntentionAction>>>();
    final List<IntentionAction> optionFixes = action.getOptionFixes();
    for (final IntentionAction optionFix : optionFixes) {
      quickFixes.add(new Pair<IntentionAction, List<IntentionAction>>(optionFix, null));
    }
    final IntentionHintComponent listPopup = new IntentionHintComponent(myProject, myEditor, intentions, quickFixes, myListPopup);
    listPopup.showPopup();
  }

  private class PopupCellRenderer implements ListCellRenderer {
    private final JList myCompositeActionsList;
    private final JPanel myPanel = new JPanel();
    private final JLabel myLabel = new JLabel();
    private final JLabel myBulbLabel = new JLabel();
    private final JButton myArrowButton = new JButton();
    private Point myLastMousePoint = null;
    private int myLastItemIndex = -1;
    private boolean myMousePressed = false;
    private final IntentionManagerSettings mySettings;

    public PopupCellRenderer(JList list) {
      myCompositeActionsList = list;
      myPanel.setLayout(new BorderLayout());
      myPanel.add(myBulbLabel, BorderLayout.WEST);
      myPanel.add(myLabel, BorderLayout.CENTER);
      myPanel.add(myArrowButton, BorderLayout.EAST);
      myBulbLabel.setIcon(mySmartTagIcon);

      myLabel.setOpaque(true);
//      myButton.setOpaque(true);
      mySettings = IntentionManagerSettings.getInstance();


      myArrowButton.setMargin(new Insets(0,0,0,0));
      myArrowButton.setBorderPainted(false);
      myArrowButton.setContentAreaFilled(false);

      myArrowButton.setPreferredSize(new Dimension(ourArrowIcon.getIconWidth() + 4, -1));
      myArrowButton.setMinimumSize(new Dimension(ourArrowIcon.getIconWidth() + 4, -1));
      myArrowButton.setMaximumSize(new Dimension(ourArrowIcon.getIconWidth() + 4, -1));
      myCompositeActionsList.addMouseMotionListener(new MouseMotionListener() {
        public void mouseDragged(MouseEvent e) {
          updateLastPoint(e);
        }

        public void mouseMoved(MouseEvent e) {
          updateLastPoint(e);
        }
      });
      myCompositeActionsList.addMouseListener(new MouseAdapter() {
        public void mouseEntered(MouseEvent e) {
          updateLastPoint(e);
        }

        public void mouseExited(MouseEvent e) {
          myLastMousePoint = null;
          myLastItemIndex = -1;
          myCompositeActionsList.repaint();
        }

        public void mousePressed(MouseEvent e) {
          e.consume();
          myMousePressed = true;
          myCompositeActionsList.repaint();
        }

        public void mouseClicked(MouseEvent e) {
          Point point = e.getPoint();
          int index = myCompositeActionsList.locationToIndex(point);
          IntentionActionWithTextCaching action = ((IntentionActionWithTextCaching)myCompositeActionsList.getModel().getElementAt(index));
          if (isInArrowButton(point) && action.getOptionIntentions().size() + action.getOptionFixes().size() > 0) {
            openChildPopup(action);
            e.consume();
          }
        }

        public void mouseReleased(MouseEvent e) {
          e.consume();
          myMousePressed = false;
          myCompositeActionsList.repaint();
        }
      });
    }


    public boolean isInBulbButton(Point point) {
      int x = point.x - myCompositeActionsList.getInsets().left;
      boolean isInButton = x <= myBulbLabel.getPreferredSize().width;
      return isInButton;
    }

    public boolean isInArrowButton(Point point) {
      return point.x - myArrowButton.getX() >= 0;
    }

    private void updateLastPoint(MouseEvent e) {
      myLastMousePoint = e.getPoint();
      myLastItemIndex = myCompositeActionsList.locationToIndex(myLastMousePoint);
      myCompositeActionsList.repaint();
    }

    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      IntentionActionWithTextCaching wrapped = (IntentionActionWithTextCaching)value;
      IntentionAction action = wrapped.getAction();
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

      myLabel.setText(" " + wrapped.getText() + " ");
      myLabel.setFont(list.getFont());

      myLabel.setForeground(fg);
      myLabel.setBackground(bg);

      if (mySettings.isShowLightBulb(action)) {
        if (myQuickFixes.contains(action)) {
          myBulbLabel.setIcon(ourQuickFixIcon);
        }
        else {
          myBulbLabel.setIcon(ourIntentionIcon);
        }
      }
      else {
        if (myQuickFixes.contains(action)) {
          myBulbLabel.setIcon(ourQuickFixOffIcon);
        }
        else {
          myBulbLabel.setIcon(ourIntentionOffIcon);
        }
      }

      myArrowButton.setBorderPainted(false);
      myArrowButton.setIcon(EmptyIcon.create(ourArrowIcon.getIconWidth(), ourArrowIcon.getIconHeight()));
      if (wrapped.getOptionIntentions().size() + wrapped.getOptionFixes().size() > 0) {
        myArrowButton.setIcon(ourArrowIcon);
        myArrowButton.getModel().setArmed(false);
        myArrowButton.getModel().setPressed(false);
        if (myLastItemIndex == index) {
          if (isInArrowButton(myLastMousePoint)) {
            myArrowButton.getModel().setArmed(myMousePressed);
            myArrowButton.getModel().setPressed(myMousePressed);
            myArrowButton.setBorderPainted(true);
          }
        }
      }
      myArrowButton.setBackground(list.getBackground());

      myPanel.setBackground(list.getBackground());
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
      if (cellRenderer.isInBulbButton(event.getPoint())) {
        IntentionAction action = ((IntentionActionWithTextCaching)getModel().getElementAt(
          locationToIndex(event.getPoint()))).getAction();
        if (IntentionManagerSettings.getInstance().isShowLightBulb(action)) {
          return CodeInsightBundle.message("lightbulb.disable.action.text", action.getFamilyName());
        }
        else {
          return CodeInsightBundle.message("lightbulb.enable.action.text", action.getFamilyName());
        }
      }
      return null;
    }

    public Point getToolTipLocation(MouseEvent event) {
      PopupCellRenderer cellRenderer = (PopupCellRenderer)getCellRenderer();
      if (cellRenderer.isInBulbButton(event.getPoint())) {
        return new Point(event.getPoint().x + 5, event.getPoint().y - 20);
      }
      return super.getToolTipLocation(event);
    }
  }

  public static class EnableDisableIntentionAction implements IntentionAction{
    private String myActionFamilyName;
    private IntentionManagerSettings mySettings = IntentionManagerSettings.getInstance();

    public EnableDisableIntentionAction(IntentionAction action) {
      myActionFamilyName = action.getFamilyName();
    }

    public String getText() {
      return mySettings.isEnabled(myActionFamilyName) ?
             CodeInsightBundle.message("disable.intention.action", myActionFamilyName) : 
             CodeInsightBundle.message("enable.intention.action", myActionFamilyName);
    }

    public String getFamilyName() {
      return getText();
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
      return true;
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      mySettings.setEnabled(myActionFamilyName, !mySettings.isEnabled(myActionFamilyName));
    }

    public boolean startInWriteAction() {
      return false;
    }
  }
}
