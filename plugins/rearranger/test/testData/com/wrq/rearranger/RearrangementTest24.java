/**
 * Submitted by Nathan Brown.  Causes NPE in Rearranger 1.7.  (Modified to compile in test directory.)
 */


import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * @author Nathan Brown
 * @version $Revision: $ $Date: $
 * @todo : doc this Copyright (c) 2001-2004 UReason.  All Rights Reserved.
 */
public abstract class TestClass extends JPanel {
  private JComponent _component;
  private boolean    _initialized;

  protected TestClass() {
    this(new BorderLayout());
  }

  protected TestClass(java.awt.LayoutManager layoutManager) {
    super(layoutManager);
    setOpaque(false);
    add(getComponent(), BorderLayout.CENTER);
    getComponent().addFocusListener(new FocusAdapter() {
      public void focusGained(FocusEvent e) {
        componentFocusGained(e);
      }

      public void focusLost(FocusEvent e) {
        componentFocusLost(e);
      }
    });
    _initialized = true;
  }

  protected void componentFocusGained(FocusEvent e) {
  }

  protected void componentFocusLost(FocusEvent e) {
  }

  protected JComponent getComponent() {
    if (_component == null) {
      _component = createComponent();
    }
    return _component;
  }

  protected abstract JComponent createComponent();

  public Dimension getPreferredSize() {
    return getComponent().getPreferredSize();
  }

  public Dimension getMaximumSize() {
    return getComponent().getMaximumSize();
  }

  public Dimension getMinimumSize() {
    return getComponent().getMinimumSize();
  }

  public void setPreferredSize(Dimension preferredSize) {
    getComponent().setPreferredSize(preferredSize);
  }

  public void setMaximumSize(Dimension maximumSize) {
    getComponent().setMaximumSize(maximumSize);
  }

  public void setMinimumSize(Dimension minimumSize) {
    getComponent().setMinimumSize(minimumSize);
  }

  public void updateUI() {
    // we're purely a container, we don't want any defaults set on us
  }

  public synchronized void addFocusListener(FocusListener l) {
    getComponent().addFocusListener(l);
  }

  public synchronized void removeFocusListener(FocusListener l) {
    getComponent().removeFocusListener(l);
  }

  public void setBorder(Border border) {
    if (_initialized) {
      getComponent().setBorder(border);
    }
    else {
      super.setBorder(border);
    }
  }

  public Border getBorder() {
    return getComponent().getBorder();
  }

  public void setToolTipText(String text) {
    getComponent().setToolTipText(text);
  }

  public Color getBackground() {
    return getComponent().getBackground();
  }

  public Color getForeground() {
    return getComponent().getForeground();
  }

  public Font getFont() {
    return getComponent().getFont();
  }

  public void setForeground(Color fg) {
    getComponent().setForeground(fg);
  }

  public void setBackground(Color bg) {
    getComponent().setBackground(bg);
  }

  public void setFont(Font font) {
    getComponent().setFont(font);
  }

  public void setEnabled(boolean enabled) {
    getComponent().setEnabled(enabled);
  }
}