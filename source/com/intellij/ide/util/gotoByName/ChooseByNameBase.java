package com.intellij.ide.util.gotoByName;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.popup.JBPopupImpl;
import com.intellij.ui.popup.PopupOwner;
import com.intellij.util.Alarm;
import com.intellij.util.diff.Diff;
import com.intellij.util.ui.UIUtil;
import org.apache.oro.text.regex.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public abstract class ChooseByNameBase{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.ChooseByNameBase");

  protected final Project myProject;
  protected final ChooseByNameModel myModel;
  protected final String myInitialText;

  protected Component myPreviouslyFocusedComponent;

  protected JPanelProvider myTextFieldPanel;// Located in the layered pane
  protected JTextField myTextField;
  protected JPanel myCardContainer;
  protected CardLayout myCard;
  protected JCheckBox myCheckBox;
  protected int myExactPrefixLen;

  protected JScrollPane myListScrollPane; // Located in the layered pane
  protected JList myList;
  private DefaultListModel myListModel;
  private List<Pair<String, Integer>> myHistory;
  private List<Pair<String, Integer>> myFuture;

  protected ChooseByNamePopupComponent.Callback myActionListener;

  protected final Alarm myAlarm = new Alarm();

  private final ListUpdater myListUpdater = new ListUpdater();

  protected boolean myListIsUpToDate = false;
  protected boolean myDisposedFlag = false;

  private String[][] myNames = new String[2][];
  private CalcElementsThread myCalcElementsThread;
  private static int VISIBLE_LIST_SIZE_LIMIT = 10;
  private static final int MAXIMUM_LIST_SIZE_LIMIT = 30;
  private int myMaximumListSizeLimit = MAXIMUM_LIST_SIZE_LIMIT;
  @NonNls private static final String NOT_FOUND_IN_PROJECT_CARD = "syslib";
  @NonNls private static final String NOT_FOUND_CARD = "nfound";
  @NonNls private static final String CHECK_BOX_CARD = "chkbox";
  @NonNls private static final String SEARCHING_CARD = "searching";
  private static final int REBUILD_DELAY = 300;

  private static class MatchesComparator implements Comparator<String> {
    private String myOriginalPattern;

    public MatchesComparator(final String originalPattern) {
      myOriginalPattern = originalPattern;
    }

    public int compare(final String a, final String b) {
      if (a.startsWith(myOriginalPattern) && b.startsWith(myOriginalPattern)) return a.compareToIgnoreCase(b);
      if (a.startsWith(myOriginalPattern) && !b.startsWith(myOriginalPattern)) return -1;
      if (b.startsWith(myOriginalPattern) && !a.startsWith(myOriginalPattern)) return 1;
      return a.compareToIgnoreCase(b);
    }
  }

  /**
   * @param initialText initial text which will be in the lookup text field
   */
  protected ChooseByNameBase(Project project, ChooseByNameModel model, String initialText) {
    myProject = project;
    myModel = model;
    myInitialText = initialText;
    myExactPrefixLen = 0;
  }

  public void invoke(final ChooseByNamePopupComponent.Callback callback, final ModalityState modalityState, boolean allowMultipleSelection) {
    initUI(callback, modalityState, allowMultipleSelection);
  }

  public class JPanelProvider extends JPanel implements DataProvider {
    JBPopup myHint = null;
    boolean myFocusRequested = false;

    JPanelProvider(LayoutManager mgr) {
      super(mgr);
    }

    JPanelProvider() {
    }

    public Object getData(String dataId) {
      if (dataId.equals(DataConstants.PSI_ELEMENT)) {
        Object element = getChosenElement();

        if (element instanceof PsiElement) {
          return element;
        }
      }
      else if (dataId.equals(DataConstantsEx.PSI_ELEMENT_ARRAY)) {
        final List<Object> chosenElements = getChosenElements();
        if (chosenElements != null) {
          List<PsiElement> result = new ArrayList<PsiElement>();
          for (Object element : chosenElements) {
            if (element instanceof PsiElement) {
              result.add((PsiElement)element);
            }
          }
          return result.toArray(new PsiElement[result.size()]);
        }
      }
      return null;
    }

    public void registerHint(JBPopup h) {
      if (myHint != null && myHint.isVisible() && myHint != h){
        myHint.cancel();
      }
      myHint = h;
    }

    public boolean focusRequested() {
      boolean focusRequested = myFocusRequested;

      myFocusRequested = false;

      return focusRequested;
    }

    public void requestFocus() {
      myFocusRequested = true;
    }

    public void unregisterHint() {
      myHint = null;
    }

    public void hideHint() {
      if (myHint != null) {
        myHint.cancel();
      }
    }

    public JBPopup getHint() {
      return myHint;
    }

    public void updateHint(PsiElement element) {
      if (myHint == null || !myHint.isVisible()) return;
      final Condition<PsiElement> popupUpdater = ((JBPopupImpl)myHint).getPopupUpdater();
      if (popupUpdater != null){
        myHint.cancel();
        popupUpdater.value(element);
      }
    }
  }



  protected void setCheckbox () {

  }

  protected void setSysLibMessage () {

  }

  /**
   * @param callback
   * @param modalityState - if not null rebuilds list in given {@link ModalityState}
   * @param allowMultipleSelection
   */
  protected void initUI(final ChooseByNamePopupComponent.Callback callback, final ModalityState modalityState, boolean allowMultipleSelection) {
    myPreviouslyFocusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);

    myActionListener = callback;
    myTextFieldPanel = new JPanelProvider();
    myTextFieldPanel.setLayout(new BoxLayout(myTextFieldPanel, BoxLayout.Y_AXIS));
    final JPanel hBox = new JPanel();
    hBox.setLayout(new BoxLayout(hBox, BoxLayout.X_AXIS));

    if (myModel.getPromptText() != null) {
      JLabel label = new JLabel(" " + myModel.getPromptText());
      label.setFont(UIUtil.getLabelFont().deriveFont(Font.BOLD));
      hBox.add(label);
    }

    myCard          = new CardLayout();
    myCardContainer = new JPanel(myCard);

    final JPanel checkBoxPanel = new JPanel();
    checkBoxPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
    myCheckBox = new JCheckBox(myModel.getCheckBoxName());
    myCheckBox.setSelected(myModel.loadInitialCheckBoxState());

    if (myModel.getPromptText() != null){
      checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.X_AXIS));
      checkBoxPanel.add (new JLabel ("  ("));
      checkBoxPanel.add (myCheckBox);
      checkBoxPanel.add (new JLabel (")"));
    } else {
      checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.LINE_AXIS));
      checkBoxPanel.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
      checkBoxPanel.add (new JLabel (")"));
      checkBoxPanel.add (myCheckBox);
      checkBoxPanel.add (new JLabel ("  ("));
    }
    checkBoxPanel.setVisible(myModel.getCheckBoxName() != null);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(checkBoxPanel, BorderLayout.CENTER);
    myCardContainer.add(panel, CHECK_BOX_CARD);
    myCardContainer.add(new JLabel("  (" + myModel.getNotInMessage() + ")"), NOT_FOUND_IN_PROJECT_CARD);
    myCardContainer.add(new JLabel("  " + IdeBundle.message("label.choosebyname.no.matches.found")), NOT_FOUND_CARD);
    myCardContainer.add(new JLabel("  " + IdeBundle.message("label.choosebyname.searching")), SEARCHING_CARD);
    myCard.show(myCardContainer, CHECK_BOX_CARD);

    //myCaseCheckBox = new JCheckBox("Ignore case");
    //myCaseCheckBox.setMnemonic('g');
    //myCaseCheckBox.setSelected(true);

    //myCamelCheckBox = new JCheckBox("Camel words");
    //myCamelCheckBox.setMnemonic('w');
    //myCamelCheckBox.setSelected(true);

    if (isCheckboxVisible()) {
      hBox.add(myCardContainer);
      //hBox.add(myCheckBox);
      //hBox.add(myCaseCheckBox);
      //hBox.add(myCamelCheckBox);
    }
    myTextFieldPanel.add(hBox);

    myHistory = new ArrayList<Pair<String, Integer>>();
    myFuture = new ArrayList<Pair<String, Integer>>();
    myTextField = new MyTextField();
    myTextField.setText(myInitialText);

    final ActionMap actionMap = new ActionMap();
    actionMap.setParent(myTextField.getActionMap());
    actionMap.put(DefaultEditorKit.copyAction, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (myTextField.getSelectedText() != null) {
          actionMap.getParent().get(DefaultEditorKit.copyAction).actionPerformed(e);
          return;
        }
        final Object chosenElement = getChosenElement();
        if (chosenElement instanceof PsiElement) {
          CopyReferenceAction.doCopy((PsiElement)chosenElement, myProject);
        }
      }
    });
    myTextField.setActionMap(actionMap);

    myTextFieldPanel.add(myTextField);
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Font editorFont = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
    myTextField.setFont(editorFont);

    if (isCloseByFocusLost()) {
      myTextField.addFocusListener(new FocusAdapter() {
        public void focusLost(FocusEvent e) {
          if (!myTextFieldPanel.focusRequested()) {
            doClose(false);
            myTextFieldPanel.hideHint();
          }
        }
      });
    }

    myCheckBox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        ensureNamesLoaded(myCheckBox.isSelected());
        rebuildList(0, REBUILD_DELAY, null, ModalityState.current());
      }
    });
    myCheckBox.setFocusable(false);

    myTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        rebuildList(0, REBUILD_DELAY, null, ModalityState.current());
        choosenElementMightChange();
      }
    });

    myTextField.addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (!myListScrollPane.isVisible()) {
          return;
        }
        final int keyCode = e.getKeyCode();
        switch (keyCode) {
          case KeyEvent.VK_DOWN:
            ListScrollingUtil.moveDown(myList, e.getModifiersEx());
            break;
          case KeyEvent.VK_UP:
            ListScrollingUtil.moveUp(myList, e.getModifiersEx());
            break;
          case KeyEvent.VK_PAGE_UP:
            ListScrollingUtil.movePageUp(myList);
            break;
          case KeyEvent.VK_PAGE_DOWN:
            ListScrollingUtil.movePageDown(myList);
            break;
          case KeyEvent.VK_ENTER:
            if (myList.getSelectedValue() == EXTRA_ELEM) {
              myMaximumListSizeLimit += MAXIMUM_LIST_SIZE_LIMIT;
              rebuildList(myList.getSelectedIndex(), REBUILD_DELAY, null, ModalityState.current());
              e.consume();
            }
            break;
        }
      }
    });

    myTextField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        doClose(true);
      }
    });

    myListModel = new DefaultListModel();
    myList = new JList(myListModel);
    myList.setFocusable(false);
    myList.setSelectionMode(allowMultipleSelection ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION :
                            ListSelectionModel.SINGLE_SELECTION);
    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (!myTextField.hasFocus()) {
          myTextField.requestFocus();
        }

        if (e.getClickCount() == 2) {
          if (myList.getSelectedValue() == EXTRA_ELEM) {
            myMaximumListSizeLimit += MAXIMUM_LIST_SIZE_LIMIT;
            rebuildList(myList.getSelectedIndex(), REBUILD_DELAY, null, ModalityState.current());
            e.consume();
          }
          else {
            doClose(true);
          }
        }
      }
    });
    myList.setCellRenderer(myModel.getListCellRenderer());
    myList.setFont(editorFont);

    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        choosenElementMightChange();
        updateDocumentation();
      }
    });

    myListScrollPane = new JScrollPane(myList);

    if (!UIUtil.isMotifLookAndFeel()) {
      UIUtil.installPopupMenuBorder(myTextFieldPanel);
    }
    UIUtil.installPopupMenuColorAndFonts(myTextFieldPanel);

    showTextFieldPanel();

    ensureNamesLoaded(myCheckBox.isSelected());

    if (modalityState != null) {
      rebuildList(0, 0, null, modalityState);
    }
  }

  private void updateDocumentation() {
    final JBPopup hint = myTextFieldPanel.getHint();
    final Object element = getChosenElement();
    if (hint != null && element instanceof PsiElement){
      myTextFieldPanel.updateHint((PsiElement)element);      
    }
  }

  private void doClose(final boolean ok) {
    myListUpdater.cancelAll();
    close(ok);
  }

  private synchronized void ensureNamesLoaded(boolean checkboxState) {
    int index = checkboxState ? 1 : 0;
    if (myNames[index] != null) return;

    Window window = (Window)SwingUtilities.getAncestorOfClass(Window.class, myTextField);
    //LOG.assertTrue (myTextField != null);
    //LOG.assertTrue (window != null);
    Window ownerWindow = null;
    if (window != null) {
      window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      ownerWindow = window.getOwner();
      if (ownerWindow != null) {
        ownerWindow.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      }
    }
    myNames[index] = myModel.getNames(checkboxState);

    if (window != null) {
      window.setCursor(Cursor.getDefaultCursor());
      if (ownerWindow != null) {
        ownerWindow.setCursor(Cursor.getDefaultCursor());
      }
    }
  }

  protected abstract boolean isCheckboxVisible();

  protected abstract boolean isShowListForEmptyPattern();

  protected abstract boolean isCloseByFocusLost();

  private void showTextFieldPanel() {
    final JLayeredPane layeredPane = getLayeredPane();
    final Dimension preferredTextFieldPanelSize = myTextFieldPanel.getPreferredSize();
    final int x = (layeredPane.getWidth() - preferredTextFieldPanelSize.width) / 2;
    final int paneHeight = layeredPane.getHeight();
    final int y = paneHeight / 3 - preferredTextFieldPanelSize.height / 2;


    myTextFieldPanel.setBounds(x, y, preferredTextFieldPanelSize.width, preferredTextFieldPanelSize.height);
    layeredPane.add(myTextFieldPanel, new Integer(500));
    layeredPane.moveToFront(myTextFieldPanel);
    VISIBLE_LIST_SIZE_LIMIT = Math.max
      (10, (paneHeight - (y + preferredTextFieldPanelSize.height)) / (preferredTextFieldPanelSize.height / 2) - 1);

    // I'm registering KeyListener to close popup only by KeyTyped event.
    // If react on KeyPressed then sometime KeyTyped goes into underlying editor.
    // It causes typing of Enter into it.
    myTextFieldPanel.registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        doClose(false);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
       JComponent.WHEN_IN_FOCUSED_WINDOW
    );

    myList.registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        doClose(false);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
       JComponent.WHEN_IN_FOCUSED_WINDOW
    );

    if (myTextField.requestFocusInWindow() || SystemInfo.isMac) {
      myTextField.requestFocus();
    }

    myTextFieldPanel.validate();
    myTextFieldPanel.paintImmediately(0, 0, myTextFieldPanel.getWidth(), myTextFieldPanel.getHeight());
  }

  private JLayeredPane getLayeredPane() {
    JLayeredPane layeredPane;
    final Window window = WindowManager.getInstance().suggestParentWindow(myProject);
    if (window instanceof JFrame) {
      layeredPane = ((JFrame)window).getLayeredPane();
    }
    else if (window instanceof JDialog) {
      layeredPane = ((JDialog)window).getLayeredPane();
    }
    else {
      throw new IllegalStateException("cannot find parent window: project=" + myProject +
                                      (myProject != null ? "; open=" + myProject.isOpen() : "") +
                                      "; window=" + window);
    }
    return layeredPane;
  }

  protected final Object myRebuildMutex = new Object ();

  protected void rebuildList(final int pos, final int delay, final Runnable postRunnable, final ModalityState modalityState) {
    myListIsUpToDate = false;
    myAlarm.cancelAllRequests();
    myListUpdater.cancelAll();
    choosenElementMightChange();
    tryToCancel();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final String text = myTextField.getText();
        if (!isShowListForEmptyPattern() && (text == null || text.trim().length() == 0)) {
          myListModel.clear();
          hideList();
          myCard.show(myCardContainer, CHECK_BOX_CARD);
          return;
        }
        final Runnable request = new Runnable() {
          public void run() {
            final CalcElementsCallback callback = new CalcElementsCallback() {
              public void run(final Set<?> elements) {
                synchronized (myRebuildMutex) {
                  ApplicationManager.getApplication().assertIsDispatchThread();
                  if (myDisposedFlag) {
                    return;
                  }

                  setElementsToList(pos, elements);

                  myListIsUpToDate = true;
                  choosenElementMightChange();

                  if (postRunnable != null) {
                    postRunnable.run();
                  }
                }
              }
            };

            tryToCancel();

            myCalcElementsThread = new CalcElementsThread(text, myCheckBox.isSelected(), callback, modalityState);
            myCalcElementsThread.setCanCancel(postRunnable == null);
            myCalcElementsThread.start();
          }
        };

        if (delay > 0) {
          myAlarm.addRequest(request, delay, ModalityState.stateForComponent(myTextField));
        }
        else {
          request.run();
        }
      }
    }, modalityState);
  }

  private void tryToCancel() {
    if (myCalcElementsThread != null) {
      myCalcElementsThread.cancel();
      myCalcElementsThread = null;
    }
  }

  private void setElementsToList(int pos, Set<?> elements) {
    myListUpdater.cancelAll();
    if (myDisposedFlag) return;
    if (elements.size() == 0) {
      myListModel.clear();
      myTextField.setForeground(Color.red);
      myListUpdater.cancelAll();
      hideList();
      return;
    }

    Object[] oldElements = myListModel.toArray();
    Object[] newElements = elements.toArray();
    Diff.Change change = Diff.buildChanges(oldElements, newElements);

    if (change == null) return; // Nothing changed

    List<Cmd> commands = new ArrayList<Cmd>();
    int inserted = 0;
    int deleted = 0;
    while (change != null) {
      if (change.deleted > 0) {
        final int start = change.line0 + inserted - deleted;
        commands.add(new RemoveCmd(start, start + change.deleted - 1));
      }

      if (change.inserted > 0) {
        for (int i = 0; i < change.inserted; i++) {
          commands.add(new InsertCmd(change.line0 + i + inserted - deleted, newElements[change.line1 + i]));
        }
      }

      deleted += change.deleted;
      inserted += change.inserted;
      change = change.link;
    }

    myTextField.setForeground(UIUtil.getTextFieldForeground());
    if (!commands.isEmpty()) {
      showList();
      myListUpdater.appendToModel(commands, pos);
    }
    else {
      ListScrollingUtil.selectItem(myList, Math.min (pos, myListModel.size () - 1));
      myList.setVisibleRowCount(Math.min(VISIBLE_LIST_SIZE_LIMIT, myList.getModel().getSize()));
      showList();
    }
  }

  private interface Cmd {
    void apply();
  }

  private class RemoveCmd implements Cmd {
    private int start;
    private int end;

    public RemoveCmd(final int start, final int end) {
      this.start = start;
      this.end = end;
    }

    public void apply() {
      myListModel.removeRange(start, end);
    }
  }

  private class InsertCmd implements Cmd {
    private int idx;
    private Object element;

    public InsertCmd(final int idx, final Object element) {
      this.idx = idx;
      this.element = element;
    }

    public void apply() {
      if (idx < myListModel.size()) {
        myListModel.add(idx, element);
      }
      else {
        myListModel.addElement(element);
      }
    }
  }

  private class ListUpdater {
    private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    private static final int DELAY = 10;
    private static final int MAX_BLOCKING_TIME = 30;

    public void cancelAll() {
      myAlarm.cancelAllRequests();
    }

    public void appendToModel(final List<Cmd> commands, final int selectionPos) {
      myAlarm.cancelAllRequests();
      if (commands.isEmpty() || myDisposedFlag) return;
      myAlarm.addRequest(new Runnable() {
        public void run() {
          if (myDisposedFlag) return;
          final long startTime = System.currentTimeMillis();
          do {
            final Cmd cmd = commands.remove(0);
            cmd.apply();
          }
          while (!commands.isEmpty() && System.currentTimeMillis() - startTime < MAX_BLOCKING_TIME);

          myList.setVisibleRowCount(Math.min(VISIBLE_LIST_SIZE_LIMIT, myList.getModel().getSize()));
          ListScrollingUtil.selectItem(myList, Math.min (selectionPos, myListModel.size () - 1));
          if (!commands.isEmpty()) {
            myAlarm.addRequest(this, DELAY);
          }
          showList();
        }
      }, DELAY);
    }
  }

  protected abstract void showList();

  protected abstract void hideList();

  protected abstract void close(boolean isOk);

  public Object getChosenElement() {
    final List<Object> elements = getChosenElements();
    return elements != null && elements.size() == 1 ? elements.get(0) : null;
  }

  protected List<Object> getChosenElements() {
    if (myListIsUpToDate) {
      List<Object> values = new ArrayList<Object>(Arrays.asList(myList.getSelectedValues()));
      values.remove(EXTRA_ELEM);
      return values;
    }

    final String text = myTextField.getText();
    final boolean checkBoxState = myCheckBox.isSelected();
    ensureNamesLoaded(checkBoxState);
    final String[] names = checkBoxState ? myNames[1] : myNames[0];
    Object uniqueElement = null;
    for (int i = 0; names != null && i < names.length; i++) {
      final String name = names[i];
      if (name.equalsIgnoreCase(text)) {
        final Object[] elements = myModel.getElementsByName(name, checkBoxState);
        if (elements.length > 1) return null;
        if (elements.length == 0) continue;
        if (uniqueElement != null) return null;
        uniqueElement = elements[0];
      }
    }
    return uniqueElement == null ? Collections.emptyList() : Collections.singletonList(uniqueElement);
  }

  protected void choosenElementMightChange() {
  }

  public static boolean isMyComponent(final Component component) {
    return component instanceof MyTextField;
  }

  private final class MyTextField extends JTextField implements PopupOwner {
    private final KeyStroke myCompletionKeyStroke;
    private final KeyStroke forwardStroke;
    private final KeyStroke backStroke;

    public MyTextField() {
      super(40);
      enableEvents(KeyEvent.KEY_EVENT_MASK);
      myCompletionKeyStroke = getShortcut(IdeActions.ACTION_CODE_COMPLETION);
      forwardStroke = getShortcut(IdeActions.ACTION_GOTO_FORWARD);
      backStroke = getShortcut(IdeActions.ACTION_GOTO_BACK);

    }

    private KeyStroke getShortcut(String actionCodeCompletion) {
      final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionCodeCompletion);
      for (final Shortcut shortcut : shortcuts) {
        if (shortcut instanceof KeyboardShortcut) {
          return ((KeyboardShortcut)shortcut).getFirstKeyStroke();
        }
      }
      return null;
    }

    protected void processKeyEvent(KeyEvent e) {
      final int caretPosition = getCaretPosition();
      if (getCaretPosition() < myExactPrefixLen) {
        myExactPrefixLen = caretPosition;
      }

      final KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
      if (myCompletionKeyStroke != null && keyStroke.equals(myCompletionKeyStroke)) {
        e.consume();
        final String pattern = myTextField.getText();
        final String oldText = myTextField.getText();
        final int oldPos = myList.getSelectedIndex();
        myHistory.add(Pair.create(oldText, oldPos));
        final Runnable postRunnable = new Runnable() {
          public void run() {
            fillInCommonPrefix(pattern);
          }
        };
        rebuildList(0, 0, postRunnable, ModalityState.current());
        return;
      }
      if (backStroke != null && keyStroke.equals(backStroke)) {
        e.consume();
        if (myHistory.size() != 0) {
          final String oldText = myTextField.getText();
          final int oldPos = myList.getSelectedIndex();
          final Pair<String, Integer> last = myHistory.remove(myHistory.size() - 1);
          myTextField.setText(last.first);
          myFuture.add(Pair.create(oldText, oldPos));
          rebuildList(0, 0, null, ModalityState.current());
        }
        return;
      }
      if (forwardStroke != null && keyStroke.equals(forwardStroke)) {
        e.consume();
        if (myFuture.size() != 0) {
          final String oldText = myTextField.getText();
          final int oldPos = myList.getSelectedIndex();
          final Pair<String, Integer> next = myFuture.remove(myFuture.size() - 1);
          myTextField.setText(next.first);
          myHistory.add(Pair.create(oldText, oldPos));
          rebuildList(0, 0, null, ModalityState.current());
        }
        return;
      }
      super.processKeyEvent(e);
    }

    private void fillInCommonPrefix(final String pattern) {
      final ArrayList<String> list = new ArrayList<String>();
      getNamesByPattern(myCheckBox.isSelected(), null, list, pattern);

      if (pattern.indexOf('*') >= 0) return; //TODO: support '*'
      final String oldText = myTextField.getText();
      final int oldPos = myList.getSelectedIndex();

      String commonPrefix  = null;
      if (list.size() != 0) {
        for (String name : list) {
          final String string = name.toLowerCase();
          if (commonPrefix == null) {
            commonPrefix = string;
          }
          else {
            while (commonPrefix.length() > 0) {
              if (string.startsWith(commonPrefix)) {
                break;
              }
              commonPrefix = commonPrefix.substring(0, commonPrefix.length() - 1);
            }
            if (commonPrefix.length() == 0) break;
          }
        }
        commonPrefix = list.get(0).substring(0, commonPrefix.length());
        for (int i = 1; i < list.size(); i++) {
          final String string = list.get(i).substring(0, commonPrefix.length());
          if (!string.equals(commonPrefix)) {
            commonPrefix = commonPrefix.toLowerCase();
            break;
          }
        }
      }
      if (commonPrefix == null) commonPrefix = "";
      final String newPattern = commonPrefix;

      myHistory.add(Pair.create(oldText, oldPos));
      myTextField.setText(newPattern);
      myTextField.setCaretPosition(newPattern.length());

      rebuildList(0, REBUILD_DELAY, null, ModalityState.current());
    }

    @Nullable
    public Point getBestPopupPosition() {
      return new Point(myTextFieldPanel.getWidth(), getHeight());
    }
  }

  private static final String EXTRA_ELEM = "...";

  private class CalcElementsThread extends Thread {
    private final String myPattern;
    private boolean myCheckboxState;
    private final CalcElementsCallback myCallback;
    private final ModalityState myModalityState;

    private Set<Object> myElements = null;

    private volatile boolean [] myCancelled = new boolean[]{false};
    private boolean myCanCancel = true;

    public CalcElementsThread(String pattern, boolean checkboxState, CalcElementsCallback callback, ModalityState modalityState) {
      myPattern = pattern;
      myCheckboxState = checkboxState;
      myCallback = callback;
      myModalityState = modalityState;
    }

    private final Alarm myShowCardAlarm = new Alarm();
    public void run() {
      showCard(SEARCHING_CARD, 200);

      final Set<Object> elements = new LinkedHashSet<Object>();
      Runnable action = new Runnable() {
        public void run() {
          try {
            ensureNamesLoaded(myCheckboxState);
            addElementsByPattern(elements, myPattern);
            for (Object elem : elements) {
              if (elem instanceof PsiElement) {
                final PsiElement psiElement = (PsiElement)elem;
                psiElement.isWritable(); // That will cache writable flag in VirtualFile. Taking the action here makes it canceled.
              }
            }
          }
          catch (ProcessCanceledException e) {
          }
        }
      };
      ApplicationManager.getApplication().runReadAction(action);

      if (myCancelled[0]) {
        myShowCardAlarm.cancelAllRequests();
        return;
      }

      final String cardToShow;
      if (elements.size() == 0 && !myCheckboxState) {
        myCheckboxState = true;
        ApplicationManager.getApplication().runReadAction(action);
        cardToShow = elements.size() == 0 ? NOT_FOUND_CARD : NOT_FOUND_IN_PROJECT_CARD;
      }
      else {
        cardToShow = elements.size() == 0 ? NOT_FOUND_CARD : CHECK_BOX_CARD;
      }
      showCard(cardToShow, 0);

      myElements = elements;

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myCallback.run(myElements);
        }
      }, myModalityState);
    }

    private void showCard(final String card, final int delay) {
      myShowCardAlarm.cancelAllRequests();
      myShowCardAlarm.addRequest(new Runnable() {
        public void run() {
          myCard.show(myCardContainer, card);
        }
      }, delay, myModalityState);
    }

    public void setCanCancel(boolean canCancel) {
      myCanCancel = canCancel;
    }


    private void addElementsByPattern(Set<Object> elementsArray, String pattern) {
      List<String> namesList = new ArrayList<String>();
      boolean overflow = getNamesByPattern(myCheckboxState, myCancelled, namesList, pattern);
      if (myCancelled[0]) {
        throw new ProcessCanceledException();
      }
      Collections.sort(namesList, new MatchesComparator(pattern));

      All:
      for (String name : namesList) {
        if (myCancelled[0]) {
          throw new ProcessCanceledException();
        }
        final Object[] elements = myModel.getElementsByName(name, myCheckboxState);
        for (final Object element : elements) {
          elementsArray.add(element);
          if (elementsArray.size() >= myMaximumListSizeLimit) {
            overflow = true;
            break All;
          }
        }
      }

      if (overflow) {
        elementsArray.add(EXTRA_ELEM);
      }
    }

    public void cancel() {
      if (myCanCancel) {
        myCancelled[0] = true;
      }
    }
  }

  private boolean getNamesByPattern(final boolean checkboxState,
                                    final boolean[] cancelled,
                                    final List<String> list,
                                    final String pattern) throws ProcessCanceledException {
    if (!isShowListForEmptyPattern()) {
      LOG.assertTrue(pattern.length() > 0);
    }

    final String[] names = checkboxState ? myNames[1] : myNames[0];
    final String regex = NameUtil.buildRegexp(pattern, myExactPrefixLen, false);

    try {
      Perl5Compiler compiler = new Perl5Compiler();
      final Pattern compiledPattern = compiler.compile(regex);
      final PatternMatcher matcher = new Perl5Matcher();

      for (String name : names) {
        if (cancelled != null && cancelled[0]) {
          throw new ProcessCanceledException();
        }

        if(matcher.matches(name, compiledPattern)) {
          list.add(name);
        }
      }
    }
    catch (MalformedPatternException e) {
    }

    return false;
  }

  private static interface CalcElementsCallback {
    void run(Set<?> elements);
  }
}
