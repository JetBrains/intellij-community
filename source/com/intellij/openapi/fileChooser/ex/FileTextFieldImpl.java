package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileTextField;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class FileTextFieldImpl implements FileLookup, Disposable, FileTextField {

  private JTextField myPathTextField;

  private CompletionResult myCurrentCompletion;
  private JBPopup myCurrentPopup;
  private JList myList;

  private MergingUpdateQueue myUiUpdater;

  private boolean myPathIsUpdating;
  private Finder myFinder;
  private LookupFilter myFilter;
  private String myCompletionBase;

  private int myCurrentCompletionsPos = 1;
  private String myFileSpitRegExp;
  public static final String KEY = "fileTextField";

  public FileTextFieldImpl(Finder finder, LookupFilter filter) {
    this(new JTextField(), finder, filter, null);
  }
  public FileTextFieldImpl(JTextField field, Finder finder, LookupFilter filter, MergingUpdateQueue uiUpdater) {
    myPathTextField = field;

    FileTextFieldImpl assigned = (FileTextFieldImpl)myPathTextField.getClientProperty(KEY);
    if (assigned != null) {
      assigned.myFinder = finder;
      assigned.myFilter = filter;
      return;
    }

    myPathTextField.putClientProperty(KEY, this);
    final boolean headless = ApplicationManager.getApplication().isUnitTestMode();

    if (uiUpdater == null) {
      myUiUpdater = new MergingUpdateQueue("FileTextField.UiUpdater", 200, false, myPathTextField);
      if (!headless) {
        new UiNotifyConnector(myPathTextField, myUiUpdater);
        Disposer.register(this, myUiUpdater);
      }
    } else {
      myUiUpdater = uiUpdater;
    }

    myFinder = finder;
    myFilter = filter;

    myFileSpitRegExp = myFinder.getSeparator().replaceAll("\\\\", "\\\\\\\\");

    myPathTextField.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(final DocumentEvent e) {
        processTextChanged();
      }

      public void removeUpdate(final DocumentEvent e) {
        processTextChanged();
      }

      public void changedUpdate(final DocumentEvent e) {
        processTextChanged();
      }
    });

    myPathTextField.addKeyListener(new KeyAdapter() {
      public void keyPressed(final KeyEvent e) {
        processListSelection(e);
      }
    });

    myPathTextField.addFocusListener(new FocusAdapter() {
      public void focusLost(final FocusEvent e) {
        closePopup();
      }
    });
  }

  public void dispose() {
  }

  private void processTextChanged() {
    suggestCompletion(false);
    onTextChanged(getTextFieldText());
  }

  protected void onTextChanged(final String newValue) {
  }

  private void suggestCompletion(final boolean selectReplacedText) {
    if (!getField().isFocusOwner()) return;

    myUiUpdater.queue(new Update("textField.suggestCompletion") {
      public void run() {
        final CompletionResult result = new CompletionResult();
        result.myCompletionBase = getCompletionBase();
        if (result.myCompletionBase == null) return;
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          public void run() {
            processCompletion(result);
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                if (!result.myCompletionBase.equals(getCompletionBase())) return;

                int pos = selectCompletionRemoveText(result, selectReplacedText);

                showCompletionPopup(result, pos);
              }
            });
          }
        });
      }
    });

  }

  private int selectCompletionRemoveText(final CompletionResult result, boolean selectReplacedText) {
    int pos = myPathTextField.getCaretPosition();

    if (result.myToComplete.size() > 0 && selectReplacedText) {
      myPathTextField.setCaretPosition(myPathTextField.getText().length());
      myPathTextField.moveCaretPosition(pos);
    }

    return pos;
  }

  public static class CompletionResult {
    public List<LookupFile> myToComplete;
    public List<LookupFile> mySiblings;
    public List<LookupFile> myKidsAfterSeparator;
    public String myCompletionBase;
    public LookupFile myClosestParent;
    public LookupFile myPreselected;
  }

  private void showCompletionPopup(final CompletionResult result, int position) {
    if (myList == null) {
      myList = new JList();
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myList.setCellRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(final JList list,
                                             final Object value,
                                             final int index,
                                             final boolean selected,
                                             final boolean hasFocus) {
          clear();
          final LookupFile file = (LookupFile)value;
          final String text = (myCurrentCompletion != null && myCurrentCompletion.myKidsAfterSeparator.contains(file) ? myFinder.getSeparator(): "") +file.getName();
          append(text, new SimpleTextAttributes(list.getFont().getStyle(), list.getForeground()));
        }
      });
    }


    if (myCurrentPopup != null) {
      closePopup();
    }

    myCurrentCompletion = result;
    myCurrentCompletionsPos = position;

    if (myCurrentCompletion.myToComplete.size() == 0) return;
    
    myList.setModel(new AbstractListModel() {
      public int getSize() {
        return myCurrentCompletion.myToComplete.size();
      }

      public Object getElementAt(final int index) {
        return myCurrentCompletion.myToComplete.get(index);
      }
    });
    myList.getSelectionModel().clearSelection();
    final PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(myList);
    myCurrentPopup = builder.setRequestFocus(false).setAutoSelectIfEmpty(false).setResizable(false).setCancelCalllback(new Computable<Boolean>() {
      public Boolean compute() {
        final int caret = myPathTextField.getCaretPosition();
        myPathTextField.setSelectionStart(caret);
        myPathTextField.setSelectionEnd(caret);
        myPathTextField.setFocusTraversalKeysEnabled(true);
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            getField().requestFocus();
          }
        });
        return Boolean.TRUE;
      }
    }).setItemChoosenCallback(new Runnable() {
      public void run() {
        processChosenFromCompletion(true);
      }
    }).createPopup();


    if (result.myPreselected != null) {
      myList.setSelectedValue(result.myPreselected, false);
    }

    myPathTextField.setFocusTraversalKeysEnabled(false);
    myCurrentPopup.showInScreenCoordinates(getField(), getLocationForCaret());
  }

  private Point getLocationForCaret() {
    Point point;

    try {
      final Rectangle rec = myPathTextField.modelToView(myPathTextField.getCaretPosition());
      point = new Point((int)rec.getMaxX(), (int)rec.getMaxY());
    }
    catch (BadLocationException e) {
      return myPathTextField.getCaret().getMagicCaretPosition();
    }

    SwingUtilities.convertPointToScreen(point, myPathTextField);

    return point;
  }

  public void processCompletion(final CompletionResult result) {
    result.myToComplete = new ArrayList<LookupFile>();
    result.mySiblings = new ArrayList<LookupFile>();
    result.myKidsAfterSeparator = new ArrayList<LookupFile>();
    String typed = result.myCompletionBase;

    final LookupFile current = getClosestParent(typed);
    result.myClosestParent = current;

    if (current == null) return;
    if (typed == null || typed.length() == 0) return;

    final String typedText = myFinder.normalize(typed);
    final boolean currentParentMatch = typedText.equals(current.getAbsolutePath());

    final boolean closedPath = typed.endsWith(myFinder.getSeparator()) && typedText.length() > myFinder.getSeparator().length();
    final String currentParentText = current.getAbsolutePath();

    if (!typedText.toUpperCase().startsWith(currentParentText.toUpperCase())) return;

    String prefix = typedText.substring(currentParentText.length());
    if (prefix.startsWith(myFinder.getSeparator())) {
      prefix = prefix.substring(myFinder.getSeparator().length());
    }
    else if (typed.endsWith(myFinder.getSeparator())) {
      prefix = "";
    }

    final String effectivePrefix = prefix.toUpperCase();

    final LookupFile currentGrandparent = current.getParent();
    final String[] grandparentPrefix = new String[1];
    if (currentGrandparent != null && currentParentMatch && !closedPath) {
      final String currentGrandparentText = currentGrandparent.getAbsolutePath();
      if (typedText.startsWith(currentGrandparentText + myFinder.getSeparator())) {
        grandparentPrefix[0] = currentParentText.substring(currentGrandparentText.length() + myFinder.getSeparator().length()).toUpperCase();
        result.myPreselected = current;
      }
    }

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        result.myToComplete.addAll(current.getChildren(new LookupFilter() {
          public boolean isAccepted(final LookupFile file) {
            return myFilter.isAccepted(file) && file.getName().toUpperCase().startsWith(effectivePrefix);
          }
        }));

        if (currentParentMatch && !closedPath) {
          result.myKidsAfterSeparator.addAll(result.myToComplete);
        }

        if (grandparentPrefix[0] != null) {
          final List<LookupFile> siblings = currentGrandparent.getChildren(new LookupFilter() {
            public boolean isAccepted(final LookupFile file) {
              return myFilter.isAccepted(file) && file.getName().toUpperCase().startsWith(grandparentPrefix[0]);
            }
          });
          result.myToComplete.addAll(siblings);
          result.mySiblings.addAll(siblings);
        }
      }
    });
  }

  private
  @Nullable
  LookupFile getClosestParent(final String typed) {
    if (typed == null) return null;
    LookupFile lastFound = myFinder.find(typed);
    if (lastFound == null) return null;
    if (lastFound.exists()) return lastFound;

    final String[] splits = myFinder.normalize(typed).split(myFileSpitRegExp);
    StringBuffer fullPath = new StringBuffer();
    for (int i = 0; i < splits.length; i++) {
      String each = splits[i];
      fullPath.append(each);
      if (i < splits.length - 1) {
        fullPath.append(myFinder.getSeparator());
      }
      final LookupFile file = myFinder.find(fullPath.toString());
      if (file == null || !file.exists()) return lastFound;
      lastFound = file;
    }

    return lastFound;
  }

  public
  @Nullable
  LookupFile getFile() {
    String text = getTextFieldText();
    if (text == null) return null;
    return myFinder.find(text);
  }

  private void processChosenFromCompletion(boolean closePath) {
    final LookupFile file = getSelectedFileFromCompletionPopup();
    if (file == null) return;

    String text = file.getAbsolutePath();
    if (closePath) {
      if (file.isDirectory() && !text.endsWith(myFinder.getSeparator()) && (getTextFieldText() != null && !getTextFieldText().endsWith(myFinder.getSeparator()))) {
        text += myFinder.getSeparator();
      }
    }
    myPathTextField.setText(text);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void processListSelection(final KeyEvent e) {
    if (togglePopup(e)) return;

    if (!isPopupShowing()) return;

    final Object action = getAction(e, myList);

    if ("selectNextRow".equals(action)) {
      ensureSelectionExists();
      ListScrollingUtil.moveDown(myList, e.getModifiersEx());
    }
    else if ("selectPreviousRow".equals(action)) {
      ListScrollingUtil.moveUp(myList, e.getModifiersEx());
    }
    else if ("scrollDown".equals(action)) {
      ListScrollingUtil.movePageDown(myList);
    }
    else if ("scrollUp".equals(action)) {
      ListScrollingUtil.movePageUp(myList);
    }
    else if (getSelectedFileFromCompletionPopup() != null && e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyChar() == File.separatorChar || e.getKeyCode() == KeyEvent.VK_TAB) {
      myCurrentPopup.cancel();
      e.consume();
      processChosenFromCompletion(e.getKeyChar() != File.separatorChar);
    }
  }

  private @Nullable LookupFile getSelectedFileFromCompletionPopup() {
    if (myList == null) return null;
    return (LookupFile)myList.getSelectedValue();
  }

  private void ensureSelectionExists() {
    if (myList.getSelectedIndex() < 0 || myList.getSelectedIndex() >= myList.getModel().getSize()) {
      if (myList.getModel().getSize() >= 0) {
        myList.setSelectedIndex(0);
      }
    }
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private boolean togglePopup(KeyEvent e) {
    final KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
    final Object action = ((InputMap)UIManager.get("ComboBox.ancestorInputMap")).get(stroke);
    if ("selectNext".equals(action)) {
      if (!isPopupShowing()) {
        suggestCompletion(true);
        return true;
      } else {
        return false;
      }
    }
    else if ("togglePopup".equals(action)) {
      if (isPopupShowing()) {
        closePopup();
      }
      else {
        suggestCompletion(true);
      }
      return true;
    }
    else {
      final Keymap active = KeymapManager.getInstance().getActiveKeymap();
      final String[] ids = active.getActionIds(stroke);
      if (ids.length > 0 && "CodeCompletion".equals(ids[0])) {
        suggestCompletion(true);
      }
    }

    return false;
  }

  private static Object getAction(final KeyEvent e, final JComponent comp) {
    final KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
    return comp.getInputMap().get(stroke);
  }


  public JTextField getField() {
    return myPathTextField;
  }

  private boolean isPopupShowing() {
    return myCurrentPopup != null && myList != null && myList.isShowing();
  }

  private void closePopup() {
    if (myCurrentPopup != null) {
      myCurrentPopup.cancel();
      myCurrentPopup = null;
    }
    myCurrentCompletion = null;
  }

  @Nullable
  public String getTextFieldText() {
    final String text = myPathTextField.getText();
    if (text == null) return null;
    return text.trim();
  }

  public final void setText(final String text, boolean now, @Nullable final Runnable onDone) {
    final Update update = new Update("pathFromTree") {
      public void run() {
        myPathIsUpdating = true;
        getField().setText(text);
        myPathIsUpdating = false;
        if (onDone != null) {
          onDone.run();
        }
      }
    };
    if (now) {
      update.run();
    }
    else {
      myUiUpdater.queue(update);
    }
  }

  public boolean isPathUpdating() {
    return myPathIsUpdating;
  }

  public @Nullable String getCompletionBase() {
    String text = getTextFieldText();
    if (text == null) return null;
    int pos = myPathTextField.getCaretPosition();
    return pos < text.length() ? text.substring(0, pos) : text;
  }

  public static class Vfs extends FileTextFieldImpl {


    public Vfs(final LookupFilter filter) {
      super(new LocalFsFinder(), filter);
    }

    public Vfs(FileChooserDescriptor filter, boolean showHidden, JTextField field) {
      super(field, new LocalFsFinder(), new LocalFsFinder.FileChooserFilter(filter, showHidden), null);
    }

    public Vfs(FileChooserDescriptor filter, boolean showHidden, final MergingUpdateQueue uiUpdater) {
      super(new JTextField(), new LocalFsFinder(), new LocalFsFinder.FileChooserFilter(filter, showHidden), uiUpdater);
    }

    public Vfs(final LookupFilter filter, final MergingUpdateQueue uiUpdater) {
      super(new JTextField(), new LocalFsFinder(), filter, uiUpdater);
    }

    public VirtualFile getSelectedFile() {
      LookupFile lookupFile = getFile();
      return lookupFile != null ? ((LocalFsFinder.VfsFile)lookupFile).getFile() : null;
    }
  }
}
