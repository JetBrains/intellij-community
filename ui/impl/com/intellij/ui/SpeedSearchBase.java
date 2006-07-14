package com.intellij.ui;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class SpeedSearchBase<Comp extends JComponent> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.SpeedSearchBase");
  private SearchPopup mySearchPopup;
  private JLayeredPane myPopupLayeredPane;
  protected final Comp myComponent;
  private final Project myProject;
  private final ToolWindowManagerListener myWindowManagerListener = new MyToolWindowManagerListener();
  private final PropertyChangeSupport myChangeSupport = new PropertyChangeSupport(this);
  private String myRecentEnteredPrefix;
  private SpeedSearchComparator myComparator = new SpeedSearchComparator();

  private static final Key SPEED_SEARCH_COMPONENT_MARKER = new Key("SPEED_SEARCH_COMPONENT_MARKER");
  @NonNls protected static final String ENTERED_PREFIX_PROPERTY_NAME = "enteredPrefix";

  public SpeedSearchBase(Comp component) {
    myComponent = component;

    if (ApplicationManager.getApplication() != null) {
      myProject = (Project)DataManager.getInstance().getDataContext(component).getData(DataConstants.PROJECT);
    }
    else {
      myProject = null;
    }

    myComponent.addFocusListener(new FocusAdapter() {
      public void focusLost(FocusEvent e) {
        manageSearchPopup(null);
      }
    });
    myComponent.addKeyListener(new KeyAdapter() {
      public void keyTyped(KeyEvent e) {
        processKeyEvent(e);
      }

      public void keyPressed(KeyEvent e) {
        processKeyEvent(e);
      }
    });

    component.putClientProperty(SPEED_SEARCH_COMPONENT_MARKER, this);
  }

  public static boolean hasActiveSpeedSearch(JComponent component) {
    SpeedSearchBase speedSearch = (SpeedSearchBase)component.getClientProperty(SPEED_SEARCH_COMPONENT_MARKER);
    return speedSearch != null && speedSearch.mySearchPopup != null && speedSearch.mySearchPopup.isVisible();
  }

  protected abstract int getSelectedIndex();

  protected abstract Object[] getAllElements();

  protected abstract String getElementText(Object element);

  protected abstract void selectElement(Object element, String selectedText);

  public void addChangeListener(PropertyChangeListener listener) {
    myChangeSupport.addPropertyChangeListener(listener);
  }

  public void removeChangeListener(PropertyChangeListener listener) {
    myChangeSupport.removePropertyChangeListener(listener);
  }

  private void fireStateChanged() {
    String enteredPrefix = getEnteredPrefix();
    myChangeSupport.firePropertyChange(ENTERED_PREFIX_PROPERTY_NAME, myRecentEnteredPrefix, enteredPrefix);
    myRecentEnteredPrefix = enteredPrefix;
  }

  protected boolean isMatchingElement(Object element, String pattern) {
    String str = getElementText(element);
    return str != null && compare(str, pattern);
  }

  protected boolean compare(String text, String pattern) {
    return myComparator.doCompare(pattern, text);
  }

  public static class SpeedSearchComparator {
    private Matcher myRecentSearchMatcher;
    private String myRecentSearchText;

    public boolean doCompare(String pattern, String text) {
      //final int asteriskIndex = pattern.indexOf('*');

      // asterisk on is of no interest
      //if (asteriskIndex==-1 || asteriskIndex == pattern.length()-1) {
      //  return text.startsWith( pattern );
      //}
      if (myRecentSearchText != null &&
          myRecentSearchText.equals(pattern)
        ) {
        myRecentSearchMatcher.reset(text);
        return myRecentSearchMatcher.find();
      }
      else {
        myRecentSearchText = pattern;
        @NonNls final StringBuffer buf = new StringBuffer(pattern.length());
        final int len = pattern.length();
        boolean hasCapitals = false;
        buf.append('^');

        for (int i = 0; i < len; ++i) {
          final char ch = pattern.charAt(i);

          // bother only * withing text
          if (ch == '*' && (i != len - 1 || i == 0)) {
            buf.append("(\\w|:)"); // ':' for xml tags
          }
          else if ("{}[].+^$*()?".indexOf(ch) != -1) {
            // do not bother with other metachars
            buf.append('\\');
          }

          if (Character.isUpperCase(ch)) {
            // for camel humps
            buf.append("[a-z]*");
            hasCapitals = true;
          }
          buf.append(ch);
        }

        try {
          final Pattern recentSearchPattern;
          recentSearchPattern = Pattern.compile(buf.toString(), hasCapitals ? 0 : Pattern.CASE_INSENSITIVE);
          return (myRecentSearchMatcher = recentSearchPattern.matcher(text)).find();
        }
        catch (PatternSyntaxException ex) {
          myRecentSearchText = null;
        }

        return false;
      }
    }
  }

  private Object findNextElement(String s) {
    String _s = s.trim();
    Object[] elements = getAllElements();
    if (elements.length == 0) return null;
    int selectedIndex = getSelectedIndex();
    for (int i = selectedIndex + 1; i < elements.length; i++) {
      Object element = elements[i];
      if (isMatchingElement(element, _s)) return element;
    }
    return selectedIndex != -1 ? elements[selectedIndex] : null; // return current
  }

  private Object findPreviousElement(String s) {
    String _s = s.trim();
    Object[] elements = getAllElements();
    if (elements.length == 0) return null;
    int selectedIndex = getSelectedIndex();
    for (int i = selectedIndex - 1; i >= 0; i--) {
      Object element = elements[i];
      if (isMatchingElement(element, _s)) return element;
    }
    return selectedIndex != -1 ? elements[selectedIndex] : null; // return current
  }

  private Object findElement(String s) {
    String _s = s.trim();
    Object[] elements = getAllElements();
    int selectedIndex = getSelectedIndex();
    if (selectedIndex < 0) {
      selectedIndex = 0;
    }
    for (int i = selectedIndex; i < elements.length; i++) {
      Object element = elements[i];
      if (isMatchingElement(element, _s)) return element;
    }
    for (int i = 0; i < selectedIndex; i++) {
      Object element = elements[i];
      if (isMatchingElement(element, _s)) return element;
    }
    return null;
  }

  private Object findFirstElement(String s) {
    String _s = s.trim();
    Object[] elements = getAllElements();
    for (Object element : elements) {
      if (isMatchingElement(element, _s)) return element;
    }
    return null;
  }

  private Object findLastElement(String s) {
    String _s = s.trim();
    Object[] elements = getAllElements();
    for (int i = elements.length - 1; i >= 0; i--) {
      Object element = elements[i];
      if (isMatchingElement(element, _s)) return element;
    }
    return null;
  }

  private void processKeyEvent(KeyEvent e) {
    if (e.isAltDown()) return;
    if (mySearchPopup != null) {
      mySearchPopup.processKeyEvent(e);
      return;
    }
    if (!isSpeedSearchEnabled()) return;
    if (e.getID() == KeyEvent.KEY_TYPED) {
      if (!UIUtil.isReallyTypedEvent(e)) return;

      char c = e.getKeyChar();
      if (Character.isLetterOrDigit(c) || c == '_' || c == '*' || c == '/') {
        manageSearchPopup(new SearchPopup(String.valueOf(c)));
        e.consume();
      }
    }
  }


  public Comp getComponent() {
    return myComponent;
  }

  protected boolean isSpeedSearchEnabled() {
    return true;
  }

  public String getEnteredPrefix() {
    return mySearchPopup != null ? mySearchPopup.mySearchField.getText() : null;
  }

  private class SearchPopup extends JPanel {
    private final SearchField mySearchField;

    public SearchPopup(String initialString) {
      final Color foregroundColor = UIUtil.getToolTipForeground();
      Color color1 = UIUtil.getToolTipBackground();
      mySearchField = new SearchField();
      final JLabel searchLabel = new JLabel(" " + UIBundle.message("search.popup.search.for.label") + " ");
      searchLabel.setFont(searchLabel.getFont().deriveFont(Font.BOLD));
      searchLabel.setForeground(foregroundColor);
      mySearchField.setBorder(null);
      mySearchField.setBackground(color1.brighter());
      mySearchField.setForeground(foregroundColor);

      mySearchField.setDocument(new PlainDocument() {
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
          String oldText;
          try {
            oldText = getText(0, getLength());
          }
          catch (BadLocationException e1) {
            oldText = "";
          }

          String newText = oldText.substring(0, offs) + str + oldText.substring(offs);
          super.insertString(offs, str, a);
          if (findElement(newText) == null) {
            mySearchField.setForeground(Color.RED);
          }
          else {
            mySearchField.setForeground(foregroundColor);
          }
        }
      });
      mySearchField.setText(initialString);

      setBorder(BorderFactory.createLineBorder(Color.gray, 1));
      setBackground(color1.brighter());
      setLayout(new BorderLayout());
      add(searchLabel, BorderLayout.WEST);
      add(mySearchField, BorderLayout.EAST);
      Object element = findElement(mySearchField.getText());
      updateSelection(element);
    }

    public void processKeyEvent(KeyEvent e) {
      mySearchField.processKeyEvent(e);
      if (e.isConsumed()) {
        int keyCode = e.getKeyCode();
        String s = mySearchField.getText();
        Object element;
        if (keyCode == KeyEvent.VK_UP) {
          element = findPreviousElement(s);
        }
        else if (keyCode == KeyEvent.VK_DOWN) {
          element = findNextElement(s);
        }
        else if (keyCode == KeyEvent.VK_HOME) {
          element = findFirstElement(s);
        }
        else if (keyCode == KeyEvent.VK_END) {
          element = findLastElement(s);
        }
        else {
          element = findElement(s);
        }
        updateSelection(element);
      }
    }

    private void updateSelection(Object element) {
      if (element != null) {
        selectElement(element, mySearchField.getText());
        mySearchField.setForeground(Color.black);
      }
      else {
        mySearchField.setForeground(Color.red);
      }
      if (mySearchPopup != null) {
        mySearchPopup.setSize(mySearchPopup.getPreferredSize());
        mySearchPopup.validate();
      }

      fireStateChanged();
    }
  }

  private class SearchField extends JTextField {
    SearchField() {
      setFocusable(false);
    }

    public Dimension getPreferredSize() {
      Dimension dim = super.getPreferredSize();
      dim.width = getFontMetrics(getFont()).stringWidth(getText()) + 10;
      return dim;
    }

    /**
     * I made this method public in order to be able to call it from the outside.
     * This is needed for delegating calls.
     */
    public void processKeyEvent(KeyEvent e) {
      int i = e.getKeyCode();
      if (i == KeyEvent.VK_BACK_SPACE && getDocument().getLength() == 0) {
        e.consume();
        return;
      }
      if (
        i == KeyEvent.VK_ENTER ||
        i == KeyEvent.VK_ESCAPE ||
        i == KeyEvent.VK_PAGE_UP ||
        i == KeyEvent.VK_PAGE_DOWN ||
        i == KeyEvent.VK_LEFT ||
        i == KeyEvent.VK_RIGHT
        ) {
        manageSearchPopup(null);
        if (i == KeyEvent.VK_ESCAPE) {
          e.consume();
        }
        return;
      }
      super.processKeyEvent(e);
      if (
        i == KeyEvent.VK_BACK_SPACE ||
        i == KeyEvent.VK_HOME ||
        i == KeyEvent.VK_END ||
        i == KeyEvent.VK_UP ||
        i == KeyEvent.VK_DOWN
        ) {
        e.consume();
      }
    }
  }

  private void manageSearchPopup(SearchPopup searchPopup) {
    if (mySearchPopup != null) {
      myPopupLayeredPane.remove(mySearchPopup);
      myPopupLayeredPane.validate();
      myPopupLayeredPane.repaint();
      myPopupLayeredPane = null;

      if (myProject != null) {
        ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).removeToolWindowManagerListener(myWindowManagerListener);
      }
    }
    else if (searchPopup != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed("ui.tree.speedsearch");
    }

    if (!myComponent.isShowing()) {
      mySearchPopup = null;
    }
    else {
      mySearchPopup = searchPopup;
    }

    fireStateChanged();

    if (mySearchPopup == null || !myComponent.isDisplayable()) return;

    if (myProject != null) {
      ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(myWindowManagerListener);
    }
    JRootPane rootPane = myComponent.getRootPane();
    if (rootPane != null) {
      myPopupLayeredPane = rootPane.getLayeredPane();
    }
    else {
      myPopupLayeredPane = null;
    }
    if (myPopupLayeredPane == null) {
      LOG.error(toString() + " in " + String.valueOf(myComponent));
      return;
    }
    myPopupLayeredPane.add(mySearchPopup, JLayeredPane.POPUP_LAYER);
    if (myPopupLayeredPane == null) return; // See # 27482. Somewho it does happen...
    Point lPaneP = myPopupLayeredPane.getLocationOnScreen();
    Point componentP = myComponent.getLocationOnScreen();
    Rectangle r = myComponent.getVisibleRect();
    Dimension prefSize = mySearchPopup.getPreferredSize();
    Window window = (Window)SwingUtilities.getAncestorOfClass(Window.class, myComponent);
    Point windowP;
    if (window instanceof JDialog) {
      windowP = ((JDialog)window).getContentPane().getLocationOnScreen();
    }
    else if (window instanceof JFrame) {
      windowP = ((JFrame)window).getContentPane().getLocationOnScreen();
    }
    else {
      windowP = window.getLocationOnScreen();
    }
    int y = r.y + componentP.y - lPaneP.y - prefSize.height;
    y = Math.max(y, windowP.y - lPaneP.y);
    mySearchPopup.setLocation(componentP.x - lPaneP.x + r.x, y);
    mySearchPopup.setSize(prefSize);
    mySearchPopup.setVisible(true);
    mySearchPopup.validate();
  }

  private class MyToolWindowManagerListener extends ToolWindowManagerAdapter {
    public void stateChanged() {
      manageSearchPopup(null);
    }
  }
}
