package com.intellij.openapi.fileChooser.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileTextField;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.concurrency.WorkerThread;
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
import java.util.ArrayList;
import java.util.List;

public abstract class FileTextFieldImpl implements FileLookup, Disposable, FileTextField {

  private JTextField myPathTextField;

  private List<LookupFile> myCurrentCompletion;
  private JBPopup myCurrentPopup;
  private JList myList;

  private MergingUpdateQueue myUiUpdater;
  private WorkerThread myWorker;

  private boolean myPathIsUpdating;
  private Finder myFinder;
  private LookupFilter myFilter;
  private String myCompletionBase;

  private int myCurrentCompletionsPos = 1;
  private String myFileSpitRegExp;

  public FileTextFieldImpl(Finder finder, LookupFilter filter) {
    this(finder, filter, null, null);
  }
  public FileTextFieldImpl(Finder finder, LookupFilter filter, MergingUpdateQueue uiUpdater, WorkerThread worker) {
    myPathTextField = new JTextField();

    if (uiUpdater == null) {
      myUiUpdater = new MergingUpdateQueue("FileTextField.UiUpdater", 200, false, myPathTextField);
      new UiNotifyConnector(myPathTextField, myUiUpdater);
      Disposer.register(this, myUiUpdater);
    } else {
      myUiUpdater = uiUpdater;
    }

    if (worker == null) {
      myWorker = new WorkerThread("FileTextField.FileLocator", 200);
      myWorker.start();
      Disposer.register(this, myWorker);
    } else {
      myWorker = worker;
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
        processListNavigation(e);
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
    suggestCompletion();
    onTextChanged(getTextFieldText());
  }

  protected void onTextChanged(final String newValue) {
  }

  private void suggestCompletion() {
    if (!getField().isFocusOwner()) return;

    myUiUpdater.queue(new Update("textField.suggestCompletion") {
      public void run() {
        final String base = getCompletionBase();
        if (base == null) return;
        myWorker.addTaskFirst(new Runnable() {
          public void run() {
            final List<LookupFile> toComplete = getCompletion(base);
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                if (!base.equals(getCompletionBase())) return;
                int pos = myPathTextField.getCaretPosition();
                myPathTextField.setCaretPosition(myPathTextField.getText().length());
                myPathTextField.moveCaretPosition(pos);
                showCompletionPopup(toComplete, pos);
              }
            });
          }
        });
      }
    });

  }

  private void showCompletionPopup(final List<LookupFile> toComplete, int position) {
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
          append(((LookupFile)value).getName(), new SimpleTextAttributes(list.getFont().getStyle(), list.getForeground()));
        }
      });
    }


    if (myCurrentPopup != null) {
      closePopup();
    }

    myCurrentCompletion = toComplete;
    myCurrentCompletionsPos = position;

    if (myCurrentCompletion.size() == 0) return;

    final Object selected = myList.getSelectedIndex() < myList.getModel().getSize() ? myList.getSelectedValue() : null;
    myList.setModel(new AbstractListModel() {
      public int getSize() {
        return myCurrentCompletion.size();
      }

      public Object getElementAt(final int index) {
        return myCurrentCompletion.get(index);
      }
    });
    if (selected != null) {
      myList.setSelectedValue(selected, true);
    }
    final PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(myList);
    myCurrentPopup = builder.setRequestFocus(false).setResizable(false).setCancelCalllback(new Computable<Boolean>() {
      public Boolean compute() {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            getField().requestFocus();
          }
        });
        return Boolean.TRUE;
      }
    }).createPopup();
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

  public List<LookupFile> getCompletion(@Nullable String typed) {
    List<LookupFile> result = new ArrayList<LookupFile>();

    LookupFile current = getClosestParent(typed);

    if (current == null) return result;
    if (typed == null || typed.length() == 0) return result;

    final String typedText = myFinder.normalize(typed);
    final String parentText = current.getAbsolutePath();

    if (!typedText.toUpperCase().startsWith(parentText.toUpperCase())) return result;

    String prefix = typedText.substring(parentText.length());
    if (prefix.startsWith(myFinder.getSeparator())) {
      prefix = prefix.substring(myFinder.getSeparator().length());
    }
    else if (typed.endsWith(myFinder.getSeparator())) {
      prefix = "";
    }

    final String effectivePrefix = prefix.toUpperCase();
    final List<LookupFile> files = current.getChildren(new LookupFilter() {
      public boolean isAccepted(final LookupFile file) {
        return myFilter.isAccepted(file) && file.getName().toUpperCase().startsWith(effectivePrefix);
      }
    });

    for (LookupFile each : files) {
      result.add(each);
    }

    return result;
  }

  private
  @Nullable
  LookupFile getClosestParent(final String typed) {
    if (typed == null) return null;
    LookupFile lastFound = myFinder.find(typed);
    if (lastFound != null && lastFound.exists()) return lastFound;

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

  private void processChosenFromCompletion(final LookupFile file) {
    if (file == null) return;
    myPathTextField.setText(file.getAbsolutePath());
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private void processListNavigation(final KeyEvent e) {
    if (togglePopup(e)) return;

    if (!isPopupShowing()) return;

    final Object action = getAction(e, myList);

    if ("selectNextRow".equals(action)) {
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
    else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
      myCurrentPopup.cancel();
      e.consume();
      processChosenFromCompletion((LookupFile)myList.getSelectedValue());
    }
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private boolean togglePopup(KeyEvent e) {
    final KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
    final Object action = ((InputMap)UIManager.get("ComboBox.ancestorInputMap")).get(stroke);
    if ("selectNext".equals(action)) {
      if (!isPopupShowing()) {
        suggestCompletion();
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
        suggestCompletion();
      }
      return true;
    }
    else {
      final Keymap active = KeymapManager.getInstance().getActiveKeymap();
      final String[] ids = active.getActionIds(stroke);
      if (ids.length > 0 && "CodeCompletion".equals(ids[0])) {
        suggestCompletion();
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

    public Vfs(FileChooserDescriptor filter, boolean showHidden) {
      super(new LocalFsFinder(), new LocalFsFinder.FileChooserFilter(filter, showHidden));
    }

    public Vfs(FileChooserDescriptor filter, boolean showHidden, final MergingUpdateQueue uiUpdater, final WorkerThread worker) {
      super(new LocalFsFinder(), new LocalFsFinder.FileChooserFilter(filter, showHidden), uiUpdater, worker);
    }

    public Vfs(final LookupFilter filter, final MergingUpdateQueue uiUpdater, final WorkerThread worker) {
      super(new LocalFsFinder(), filter, uiUpdater, worker);
    }

    public VirtualFile getSelectedFile() {
      LookupFile lookupFile = getFile();
      return lookupFile != null ? ((LocalFsFinder.VfsFile)lookupFile).getFile() : null;
    }
  }
}
