package com.intellij.ide.util.gotoByName;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ListScrollingUtilEx;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class ChooseByNameBase {
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
  private ArrayList<Pair<String, Integer>> myHistory;
  private ArrayList<Pair<String, Integer>> myFuture;

  protected Callback myActionListener;

  protected final Alarm myAlarm = new Alarm();
  protected boolean myListIsUpToDate = false;
  protected boolean myDisposedFlag = false;

  private String[][] myNames = new String[2][];
  private CalcElementsThread myCalcElementsThread;
  private static int VISIBLE_LIST_SIZE_LIMIT = 10;
  private static final int MAXIMUM_LIST_SIZE_LIMIT = 30;
  private int myMaximumListSizeLimit = MAXIMUM_LIST_SIZE_LIMIT;
  private static final String NOT_FOUND_MESSAGE_CARD = "syslib";
  private static final String NOT_FOUND_CARD = "nfound";
  private static final String CHECK_BOX_CARD = "chkbox";
  static final int REBUILD_DELAY = 100;

  private static class IgnoreCaseComparator implements Comparator<String> {
    public int compare(String a, String b) {
      return a.compareToIgnoreCase(b);
    }
  }

  private static final Comparator<String> UCS_COMPARATOR = new IgnoreCaseComparator();

  public static abstract class Callback {
    public abstract void elementChosen(Object element);
    public void onClose() { }
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

  public void invoke(final Callback callback, final ModalityState modalityState, boolean allowMultipleSelection) {
    initUI(callback, modalityState, allowMultipleSelection);
  }

  public class JPanelProvider extends JPanel implements DataProvider {
    LightweightHint myHint = null;
    boolean myFocusRequested = false;

    JPanelProvider(LayoutManager mgr) {
      super(mgr);
    }

    JPanelProvider() {
    }

    public Object getData(String dataId) {
      if (dataId.equals(DataConstants.PSI_ELEMENT)) {
        Object element = getChosenElement();

        if (element != null && element instanceof PsiElement) {
          return element;
        }
      }

      return null;
    }

    public void registerHint(LightweightHint h) {
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
        myHint.hide();
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
  protected void initUI(final Callback callback, final ModalityState modalityState, boolean allowMultipleSelection) {
    myActionListener = callback;
    //myTextFieldPanel = new JPanelProvider(new GridBagLayout());
    myTextFieldPanel = new JPanelProvider();
    myTextFieldPanel.setLayout(new BoxLayout(myTextFieldPanel, BoxLayout.Y_AXIS));
    final JPanel hBox = new JPanel();
    hBox.setLayout(new BoxLayout(hBox, BoxLayout.X_AXIS));

    if (myModel.getPromptText() != null) {
      JLabel label = new JLabel(" " + myModel.getPromptText());
      label.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
      hBox.add(label);
    }

    myCard          = new CardLayout();
    myCardContainer = new JPanel(myCard);

    final JPanel checkBoxPanel = new JPanel();
    checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.X_AXIS));

    checkBoxPanel.add (new JLabel ("  ("));
    myCheckBox = new JCheckBox(myModel.getCheckBoxName() + " )");
    myCheckBox.setMnemonic(myModel.getCheckBoxMnemonic());
    myCheckBox.setSelected(myModel.loadInitialCheckBoxState());
    checkBoxPanel.add (myCheckBox);


    myCardContainer.add(checkBoxPanel, CHECK_BOX_CARD);
    myCardContainer.add(new JLabel("  (" + myModel.getNotInMessage() + ")"), NOT_FOUND_MESSAGE_CARD);
    myCardContainer.add(new JLabel("  (no matches found)"           ), NOT_FOUND_CARD);
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

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    Font editorFont = new Font(scheme.getEditorFontName(), Font.PLAIN, scheme.getEditorFontSize());
    myTextField.setFont(editorFont);
    myTextFieldPanel.add(myTextField,
                         new GridBagConstraints(0, 1, 3, 1, 1, 0, GridBagConstraints.EAST, GridBagConstraints.HORIZONTAL,
                                                new Insets(0, 0, 0, 0), 0, 0));

    if (isCloseByFocusLost()) {
      myTextField.addFocusListener(new FocusAdapter() {
        public void focusLost(FocusEvent e) {
          if (!myTextFieldPanel.focusRequested()) {
            close(false);
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
        if (myListScrollPane.isVisible()) {
          if (e.getKeyCode() == KeyEvent.VK_DOWN) {
            ListScrollingUtilEx.moveDown(myList, e.getModifiersEx());
          }
          else if (e.getKeyCode() == KeyEvent.VK_UP) {
            ListScrollingUtilEx.moveUp(myList, e.getModifiersEx());
          }
          else if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
            ListScrollingUtil.movePageUp(myList);
          }
          else if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
            ListScrollingUtil.movePageDown(myList);
          }
          else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            if (myList.getSelectedValue() instanceof ExtraElem) {
              myMaximumListSizeLimit += MAXIMUM_LIST_SIZE_LIMIT;
              rebuildList(myList.getSelectedIndex(), REBUILD_DELAY, null, ModalityState.current());
              e.consume();
            }
          }
        }
      }
    });

    myTextField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent actionEvent) {
        close(true);
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
          if (myList.getSelectedValue() instanceof ExtraElem) {
            myMaximumListSizeLimit += MAXIMUM_LIST_SIZE_LIMIT;
            rebuildList(myList.getSelectedIndex(), REBUILD_DELAY, null, ModalityState.current());
            e.consume();
          }
          else
            close(true);
        }
      }
    });
    myList.setCellRenderer(myModel.getListCellRenderer());
    myList.setFont(editorFont);

    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        choosenElementMightChange();
      }
    });

    myListScrollPane = new JScrollPane(myList);

    if (!"Motif".equals(UIManager.getLookAndFeel().getID())) {
      LookAndFeel.installBorder(myTextFieldPanel, "PopupMenu.border");
    }
    LookAndFeel.installColorsAndFont(myTextFieldPanel, "PopupMenu.background", "PopupMenu.foreground", "PopupMenu.font");
    LookAndFeel.installColorsAndFont(myCheckBox, "PopupMenu.background", "PopupMenu.foreground", "PopupMenu.font");

    showTextFieldPanel();

    ensureNamesLoaded(myCheckBox.isSelected());

    if (modalityState != null) {
      rebuildList(0, 0, null, modalityState);
    }
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
    // If react on KeyPressed then sometime KeyTyped goes into underlying myEditor.
    // It causes typing of Enter into it.
    myTextFieldPanel.registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        close(false);
      }
    },
                                            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                            JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    myList.registerKeyboardAction(new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        close(false);
      }
    },
                                  KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                  JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myPreviouslyFocusedComponent = WindowManagerEx.getInstanceEx().getFocusedComponent(myProject);
    if (myTextField.requestFocusInWindow()) {
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
    choosenElementMightChange();
    tryToCancel();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final String text = myTextField.getText();
        if (!isShowListForEmptyPattern() && (text == null || text.trim().length() == 0)) {
          myListModel.clear();
          hideList();
          return;
        }
        final Runnable request = new Runnable() {
          public void run() {
            final Runnable request = this;
            LOG.info ("Rebuild " + hashCode() + " started");
            final CalcElementsCallback callback = new CalcElementsCallback() {
              public void run(final ArrayList<?> elements) {
                synchronized (myRebuildMutex) {
                  LOG.info ("Rebuild callback " + hashCode() + " of " + request.hashCode()  + " started");
                  ApplicationManager.getApplication().assertIsDispatchThread();
                  if (myDisposedFlag) {
                    LOG.info ("Rebuild callback " + hashCode() + " of " + request.hashCode() + " disposed");
                    return;
                  }

                  setElementsToList(pos, elements);

                  myListIsUpToDate = true;
                  choosenElementMightChange();

                  if (postRunnable != null) {
                    postRunnable.run();
                  }
                  LOG.info ("Rebuild callback " + hashCode () + " of " + request.hashCode () + " finished");
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
          LOG.info ("Rebuild " + this + " posted with delay " + delay, new Throwable ());
          myAlarm.addRequest(request, delay);
        }
        else {
          LOG.info ("Rebuild " + this + " posted", new Throwable ());
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

  private void setElementsToList(int pos, ArrayList<?> elements) {
    if (myDisposedFlag) return;
    myListModel.clear();
    if (elements.size() == 0) {
      myTextField.setForeground(Color.red);
      hideList();
      return;
    }

    myTextField.setForeground(UIManager.getColor("TextField.foreground"));
    for (int i = 0; i < elements.size(); i++) {
      myListModel.addElement(elements.get(i));
    }
    //if (isTooLong){
    //  myListModel.addElement("...");
    //}
    ListScrollingUtil.selectItem(myList, Math.min (pos, myListModel.size () - 1));
    myList.setVisibleRowCount(Math.min(VISIBLE_LIST_SIZE_LIMIT, myList.getModel().getSize()));
    showList();
  }

  protected abstract void showList();

  protected abstract void hideList();

  protected abstract void close(boolean isOk);

  public Object getChosenElement() {
    final Object[] elements = getChosenElements();
    return (elements != null && elements.length == 1) ? elements[0] : null;
  }

  protected Object[] getChosenElements() {
    if (myListIsUpToDate) {
      final List<Object> values = new ArrayList<Object>(Arrays.asList(myList.getSelectedValues()));
      values.remove(OUR_EXTRA_ELEMENT);
      return values.toArray(new Object[values.size()]);
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
    return uniqueElement == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : new Object[] {uniqueElement};
  }

  protected void choosenElementMightChange() {
  }

  public static boolean isMyComponent(final Component component) {
    return component instanceof MyTextField;
  }

  private final class MyTextField extends JTextField {
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
      for (int i = 0; i < shortcuts.length; i++) {
        final Shortcut shortcut = shortcuts[i];
        if (shortcut instanceof KeyboardShortcut) {
          return ((KeyboardShortcut)shortcut).getFirstKeyStroke();
        }
      }
      return null;
    }

    protected void processKeyEvent(KeyEvent e) {
      {
        final int caretPosition = getCaretPosition();
        if (getCaretPosition() < myExactPrefixLen) {
          myExactPrefixLen = caretPosition;
        }
      }

      final KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
      if (myCompletionKeyStroke != null && keyStroke.equals(myCompletionKeyStroke)) {
        e.consume();
        final String pattern = myTextField.getText();
        final String oldText = myTextField.getText();
        final int oldPos = myList.getSelectedIndex();
        myHistory.add(Pair.create(oldText, new Integer(oldPos)));
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
          //final String pattern = myTextField.getText();
          //final Object current = myList.getSelectedValue();
          final Pair<String, Integer> last = myHistory.remove(myHistory.size() - 1);
          myTextField.setText(last.first);
          myFuture.add(Pair.create(oldText, new Integer(oldPos)));
          rebuildList(0, 0, null, ModalityState.current());
          //myList.setSelectedIndex(last.second.intValue());
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
          myHistory.add(Pair.create(oldText, new Integer(oldPos)));
          rebuildList(0, 0, null, ModalityState.current());
          //myList.setSelectedIndex(next.second.intValue());
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

      final String newPattern;
      {
        String commonPrefix  = null;
        if (list.size() != 0) {
          for (int i = 0; i < list.size(); i++) {
            final String string = list.get(i).toLowerCase();
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
        newPattern = commonPrefix;
      }
      myHistory.add(Pair.create(oldText, new Integer(oldPos)));
      myTextField.setText(newPattern);
      myTextField.setCaretPosition(newPattern.length());

      rebuildList(0, REBUILD_DELAY, null, ModalityState.current());
    }
  }

  private static class ExtraElem {
    public String toString () { return "..."; }
  }
  private static final ExtraElem OUR_EXTRA_ELEMENT = new ExtraElem();

  private class CalcElementsThread extends Thread {
    private final String myPattern;
    private boolean myCheckboxState;
    private final CalcElementsCallback myCallback;
    private final ModalityState myModalityState;

    //private boolean myIsTooLong;
    private ArrayList<?> myElements = null;

    private volatile boolean [] myCancelled = new boolean[]{false};
    private boolean myCanCancel = true;
    //private boolean endsWithSpace;
    //private boolean noUpperCase;

    public CalcElementsThread(String pattern, boolean checkboxState, CalcElementsCallback callback, ModalityState modalityState) {
      myPattern = pattern;
      myCheckboxState = checkboxState;
      myCallback = callback;
      myModalityState = modalityState;
    }

    public void run() {
      final ArrayList<Object> elements = new ArrayList<Object>();
      Runnable action = new Runnable() {
        public void run() {
          try {
            ensureNamesLoaded(myCheckboxState);
            addElementsByPattern(elements, myPattern);
          }
          catch (ProcessCanceledException e) {
          }
        }
      };
      ApplicationManager.getApplication().runReadAction(action);

      if (myCancelled[0]) return;

      if (elements.size() == 0 && !myCheckboxState) {
        myCard.show(myCardContainer, NOT_FOUND_MESSAGE_CARD);
        myCheckboxState = true;
        ApplicationManager.getApplication().runReadAction(action);
      } else {
        myCard.show(myCardContainer, CHECK_BOX_CARD);
      }
      if (elements.size() == 0)
        myCard.show(myCardContainer, NOT_FOUND_CARD);

      myElements = elements;


      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myCallback.run(myElements);
        }
      }, myModalityState);
    }

    public void setCanCancel(boolean canCancel) {
      myCanCancel = canCancel;
    }


    private void addElementsByPattern(ArrayList<Object> elementsArray, String pattern) { //throws ProcessCanceledException {
      ArrayList<String> namesList = new ArrayList<String>();
      boolean ov = getNamesByPattern(myCheckboxState, myCancelled, namesList, pattern);
      if (myCancelled[0])
        throw new ProcessCanceledException();
      Collections.sort(namesList, UCS_COMPARATOR);

      All:
      for (int i = 0; i < namesList.size(); i++) {
        if (myCancelled[0])
          throw new ProcessCanceledException();
        final Object o = namesList.get(i);
        final String name = (String)o;
        final Object[] elements = myModel.getElementsByName(name, myCheckboxState);
        for (int j = 0; j < elements.length; j++) {
          final Object element = elements[j];
          elementsArray.add(element);
          if (elementsArray.size() >= myMaximumListSizeLimit) {
            ov = true;
            break All;
          }

        }

      }

      if (ov) {
        elementsArray.add(OUR_EXTRA_ELEMENT);
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
                                  final ArrayList<String> list,
                                  final String pattern) throws ProcessCanceledException {
    if (!isShowListForEmptyPattern()) {
      LOG.assertTrue(pattern.length() > 0);
    }

    boolean ov = false;
    final String[] names = checkboxState ? myNames[1] : myNames[0];
    final Pair<String,String> pref_regex = buildRegexp(pattern);
    final String regex = pref_regex.getSecond();

    try {
      final Pattern compiledPattern = Pattern.compile(regex);
      final Matcher matcher = compiledPattern.matcher("");
      for (int i = 0; i < names.length; i++) {
        if (cancelled != null && cancelled[0]) {
          throw new ProcessCanceledException();
        }
        final String name = names[i];
        if (matcher.reset(name).matches()) {
          list.add(name);
        }
      }
    }
    catch (PatternSyntaxException e) {
    }

    return ov;
  }

  private static boolean containsOnlyUppercaseLetters(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c != '*' && !Character.isUpperCase(c)) return false;
    }
    return true;
  }

  private Pair<String,String> buildRegexp(String pattern) {
    String pref;
    {
      final int len = pattern.length ();
      int i = 0;
      while (i != len && (Character.isLetterOrDigit(pattern.charAt(i)) && (i == 0 || !Character.isUpperCase(pattern.charAt(i)))))
        ++ i;
      pref = pattern.substring(0,i);
    }


    {
      final int eol = pattern.indexOf ('\n');
      if (eol != -1)
        pattern = pattern.substring (0, eol);
    }
    if (pattern.length () >= 80)
      pattern = pattern.substring (0, 80);

    final StringBuffer buffer = new StringBuffer();
    boolean lastIsUppercase = false;
    final boolean endsWithSpace = StringUtil.endsWithChar(pattern, ' ');
    final boolean uppercaseOnly = containsOnlyUppercaseLetters(pattern);
    pattern = pattern.trim();
    myExactPrefixLen = Math.min(myExactPrefixLen, pattern.length());
    for (int i = 0; i != myExactPrefixLen; ++i) {
      final char c = pattern.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        buffer.append(c);
      }
      else {
        buffer.append("\\u");
        buffer.append(Integer.toHexString(c + 0x20000).substring(1));
      }
    }
    for (int i = myExactPrefixLen; i < pattern.length(); i++) {
      final char c = pattern.charAt(i);
      lastIsUppercase = false;
      if (Character.isLetterOrDigit(c)) {
        // This logic allows to use uppercase letters only to catch the name like PDM for PsiDocumentManager
        if (Character.isUpperCase(c)) {
          if (!uppercaseOnly) {
            buffer.append('(');
          }
          if (i > 0) buffer.append("[a-z0-9]*");
          buffer.append(c);
          if (!uppercaseOnly) {
            buffer.append('|');
            buffer.append(Character.toLowerCase(c));
            buffer.append(')');
          }
          lastIsUppercase = true;
        }
        else if (Character.isLowerCase(c)) {
          buffer.append('[');
          buffer.append(c);
          buffer.append('|');
          buffer.append(Character.toUpperCase(c));
          buffer.append(']');
        }
        else {
          buffer.append(c);
        }
      }
      else if (c == '*') {
        buffer.append(".*");
      }
      else {
        buffer.append("\\u");
        buffer.append(Integer.toHexString(c + 0x20000).substring(1));
      }
    }

    if (!endsWithSpace) {
      buffer.append(".*");
    }
    else if (lastIsUppercase) {
      buffer.append("[a-z0-9]*");
    }

    final String regex = buffer.toString();
    return Pair.create (pref, regex);
  }

  private static interface CalcElementsCallback {
    void run(ArrayList<?> elements);
  }
}
