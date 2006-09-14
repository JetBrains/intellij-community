package com.intellij.ui;

import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;

import org.jetbrains.annotations.NonNls;

public abstract class MultilineTreeCellRenderer extends JComponent implements javax.swing.tree.TreeCellRenderer {

  private boolean myWrapsCalculated = false;
  private boolean myTooSmall = false;
  private int myHeightCalculated = -1;
  private int myWrapsCalculatedForWidth = -1;

  private ArrayList myWraps = new ArrayList();

  private int myMinHeight = 1;
  private Insets myTextInsets;
  private Insets myLabelInsets = new Insets(1, 2, 1, 2);

  private boolean mySelected;
  private boolean myHasFocus;

  private Icon myIcon;
  private String[] myLines = ArrayUtil.EMPTY_STRING_ARRAY;
  private String myPrefix;
  private int myTextLength;
  private int myPrefixWidth;
  @NonNls protected static final String FONT_PROPERTY_NAME = "font";


  public MultilineTreeCellRenderer() {
    myTextInsets = new Insets(0,0,0,0);

    addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        onSizeChanged();
      }
    });

    addPropertyChangeListener(new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (FONT_PROPERTY_NAME.equalsIgnoreCase(evt.getPropertyName())) {
          onFontChanged();
        }
      }
    });

  }

  protected void setMinHeight(int height) {
    myMinHeight = height;
    myHeightCalculated = Math.max(myMinHeight, myHeightCalculated);
  }

  protected void setTextInsets(Insets textInsets) {
    myTextInsets = textInsets;
    onSizeChanged();
  }

  private void onFontChanged() {
    myWrapsCalculated = false;
  }

  private void onSizeChanged() {
    int currWidth = getWidth();
    if (currWidth != myWrapsCalculatedForWidth) {
      myWrapsCalculated = false;
      myHeightCalculated = -1;
      myWrapsCalculatedForWidth = -1;
    }
  }

  private FontMetrics getCurrFontMetrics() {
    return getFontMetrics(getFont());
  }

  public void paint(Graphics g) {
    int height = getHeight();
    int width = getWidth();
    int borderX = myLabelInsets.left - 1;
    int borderY = myLabelInsets.top - 1;
    int borderW = width - borderX - myLabelInsets.right + 2;
    int borderH = height - borderY - myLabelInsets.bottom + 1;

    if (myIcon != null) {
      int verticalIconPosition = (height - myIcon.getIconHeight())/2;
      myIcon.paintIcon(this, g, 0, verticalIconPosition);
      borderX += myIcon.getIconWidth();
      borderW -= myIcon.getIconWidth();
    }

    Color bgColor;
    Color fgColor;
    if (mySelected && myHasFocus){
      bgColor = UIUtil.getTreeSelectionBackground();
      fgColor = UIUtil.getTreeSelectonForeground();
    }
    else{
      bgColor = UIUtil.getTreeTextBackground();
      fgColor = getForeground();
    }

    // fill background
    g.setColor(bgColor);
    g.fillRect(borderX, borderY, borderW, borderH);

    // draw border
    if (mySelected) {
      g.setColor(UIUtil.getTreeSelectionBorderColor());
      UIUtil.drawDottedRectangle(g, borderX, borderY, borderX + borderW - 1, borderY + borderH - 1);
    }

    // paint text
    recalculateWraps();

    if (myTooSmall) { // TODO ???
      return;
    }

    int fontHeight = getCurrFontMetrics().getHeight();
    int currBaseLine = getCurrFontMetrics().getAscent();
    currBaseLine += myTextInsets.top;
    g.setFont(getFont());
    g.setColor(fgColor);

    if (myPrefix != null) {
      g.drawString(myPrefix, myTextInsets.left - myPrefixWidth + 1, currBaseLine);
    }

    for (int i = 0; i < myWraps.size(); i++) {
      String currLine = (String)myWraps.get(i);
      g.drawString(currLine, myTextInsets.left, currBaseLine);
      currBaseLine += fontHeight;  // first is getCurrFontMetrics().getAscent()
    }
  }

  public void setText(String[] lines, String prefix) {
    myLines = lines;
    myTextLength = 0;
    for (int i = 0; i < lines.length; i++) {
      myTextLength += lines[i].length();
    }
    myPrefix = prefix;

    myWrapsCalculated = false;
    myHeightCalculated = -1;
    myWrapsCalculatedForWidth = -1;
  }

  public void setIcon(Icon icon) {
    myIcon = icon;

    myWrapsCalculated = false;
    myHeightCalculated = -1;
    myWrapsCalculatedForWidth = -1;
  }

  public Dimension getMinimumSize() {
    if (getFont() != null) {
      int minHeight = getCurrFontMetrics().getHeight();
      return new Dimension(minHeight, minHeight);
    }
    return new Dimension(
      MIN_WIDTH + myTextInsets.left + myTextInsets.right,
      MIN_WIDTH + myTextInsets.top + myTextInsets.bottom
    );
  }

  private static final int MIN_WIDTH = 10;

  // Calculates height for current width.
  public Dimension getPreferredSize() {
    recalculateWraps();
    return new Dimension(myWrapsCalculatedForWidth, myHeightCalculated);
  }

  // Calculate wraps for the current width
  private void recalculateWraps() {
    int currwidth = getWidth();
    if (myWrapsCalculated) {
      if (currwidth == myWrapsCalculatedForWidth) {
        return;
      }
      else {
        myWrapsCalculated = false;
      }
    }
    int wrapsCount = calculateWraps(currwidth);
    myTooSmall = (wrapsCount == -1);
    if (myTooSmall) {
      wrapsCount = myTextLength;
    }
    int fontHeight = getCurrFontMetrics().getHeight();
    myHeightCalculated = wrapsCount * fontHeight + myTextInsets.top + myTextInsets.bottom;
    myHeightCalculated = Math.max(myMinHeight, myHeightCalculated);

    int maxWidth = 0;
    for (int i=0; i < myWraps.size(); i++) {
      String s = (String)myWraps.get(i);
      int width = getCurrFontMetrics().stringWidth(s);
      maxWidth = Math.max(maxWidth, width);
    }

    myWrapsCalculatedForWidth = myTextInsets.left + maxWidth + myTextInsets.right;
    myWrapsCalculated = true;
  }

  private int calculateWraps(int width) {
    myTooSmall = width < MIN_WIDTH;
    if (myTooSmall) {
      return -1;
    }

    int result = 0;
    myWraps = new ArrayList();

    for (int i = 0; i < myLines.length; i++) {
      String aLine = myLines[i];
      int lineFirstChar = 0;
      int lineLastChar = aLine.length() - 1;
      int currFirst = lineFirstChar;
      int printableWidth = width - myTextInsets.left - myTextInsets.right;
      if (aLine.length() == 0) {
        myWraps.add(aLine);
        result++;
      }
      else {
        while (currFirst <= lineLastChar) {
          int currLast = calculateLastVisibleChar(aLine, printableWidth, currFirst, lineLastChar);
          if (currLast < lineLastChar) {
            int currChar = currLast + 1;
            if (!Character.isWhitespace(aLine.charAt(currChar))) {
              while (currChar >= currFirst) {
                if (Character.isWhitespace(aLine.charAt(currChar))) {
                  break;
                }
                currChar--;
              }
              if (currChar > currFirst) {
                currLast = currChar;
              }
            }
          }
          myWraps.add(aLine.substring(currFirst, currLast + 1));
          currFirst = currLast + 1;
          while ((currFirst <= lineLastChar) && (Character.isWhitespace(aLine.charAt(currFirst)))) {
            currFirst++;
          }
          result++;
        }
      }
    }
    return result;
  }

  private int calculateLastVisibleChar(String line, int viewWidth, int firstChar, int lastChar) {
    if (firstChar == lastChar) return lastChar;
    if (firstChar > lastChar) throw new IllegalArgumentException("firstChar=" + firstChar + ", lastChar=" + lastChar);
    int totalWidth = getCurrFontMetrics().stringWidth(line.substring(firstChar, lastChar + 1));
    if (totalWidth == 0 || viewWidth > totalWidth) {
      return lastChar;
    }
    else {
      int newApprox = (lastChar - firstChar + 1) * viewWidth / totalWidth;
      int currChar = firstChar + Math.max(newApprox - 1, 0);
      int currWidth = getCurrFontMetrics().stringWidth(line.substring(firstChar, currChar + 1));
      while (true) {
        if (currWidth > viewWidth) {
          currChar--;
          if (currChar <= firstChar) {
            return firstChar;
          }
          currWidth -= getCurrFontMetrics().charWidth(line.charAt(currChar + 1));
          if (currWidth <= viewWidth) {
            return currChar;
          }
        }
        else {
          currChar++;
          if (currChar > lastChar) {
            return lastChar;
          }
          currWidth += getCurrFontMetrics().charWidth(line.charAt(currChar));
          if (currWidth >= viewWidth) {
            return currChar - 1;
          }
        }
      }
    }
  }

  private int getChildIndent(JTree tree) {
    TreeUI newUI = tree.getUI();
    if (newUI instanceof javax.swing.plaf.basic.BasicTreeUI) {
      javax.swing.plaf.basic.BasicTreeUI btreeui = (javax.swing.plaf.basic.BasicTreeUI)newUI;
      return btreeui.getLeftChildIndent() + btreeui.getRightChildIndent();
    }
    else {
      return ((Integer)UIUtil.getTreeLeftChildIndent()).intValue() + ((Integer)UIUtil.getTreeRightChildIndent()).intValue();
    }
  }

  private int getAvailableWidth(Object forValue, JTree tree) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)forValue;
    int busyRoom = tree.getInsets().left + tree.getInsets().right + getChildIndent(tree) * node.getLevel();
    return tree.getVisibleRect().width - busyRoom - 2;
  }

  protected abstract void initComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus);

  public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    setFont(UIUtil.getTreeFont());

    initComponent(tree, value, selected, expanded, leaf, row, hasFocus);

    mySelected = selected;
    myHasFocus = hasFocus;

    int availWidth = getAvailableWidth(value, tree);
    if (availWidth > 0) {
      setSize(availWidth, 100);     // height will be calculated automatically
    }

    int leftInset = myLabelInsets.left;

    if (myIcon != null) {
      leftInset += myIcon.getIconWidth() + 2;
    }

    if (myPrefix != null) {
      myPrefixWidth = getCurrFontMetrics().stringWidth(myPrefix) + 5;
      leftInset += myPrefixWidth;
    }

    setTextInsets(new Insets(myLabelInsets.top, leftInset, myLabelInsets.bottom, myLabelInsets.right));
    if (myIcon != null) {
      setMinHeight(myIcon.getIconHeight());
    }
    else {
      setMinHeight(1);
    }

    setSize(getPreferredSize());
    recalculateWraps();

    return this;
  }

  public static JScrollPane installRenderer(final JTree tree, final MultilineTreeCellRenderer renderer) {
    final TreeCellRenderer defaultRenderer = tree.getCellRenderer();

    JScrollPane scrollpane = new JScrollPane(tree){
      private int myAddRemoveCounter = 0;
      private boolean myShouldResetCaches = false;
      public void setSize(Dimension d) {
        boolean isChanged = getWidth() != d.width || myShouldResetCaches;
        super.setSize(d);
        if (isChanged) resetCaches();
      }

      public void reshape(int x, int y, int w, int h) {
        boolean isChanged = w != getWidth() || myShouldResetCaches;
        super.reshape(x, y, w, h);
        if (isChanged) resetCaches();
      }

      private void resetCaches() {
        resetHeightCache(tree, defaultRenderer, renderer);
        myShouldResetCaches = false;
      }

      public void addNotify() {
        super.addNotify();    //To change body of overriden methods use Options | File Templates.
        if (myAddRemoveCounter == 0) myShouldResetCaches = true;
        myAddRemoveCounter++;
      }

      public void removeNotify() {
        super.removeNotify();    //To change body of overriden methods use Options | File Templates.
        myAddRemoveCounter--;
      }
    };
    scrollpane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    scrollpane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

    tree.setCellRenderer(renderer);

    scrollpane.addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        resetHeightCache(tree, defaultRenderer, renderer);
      }

      public void componentShown(ComponentEvent e) {
        // componentResized not called when adding to opened tool window.
        // Seems to be BUG#4765299, however I failed to create same code to reproduce it.
        // To reproduce it with IDEA: 1. remove this method, 2. Start any Ant task, 3. Keep message window open 4. start Ant task again.
        resetHeightCache(tree, defaultRenderer, renderer);
      }
    });

    return scrollpane;
  }

  private static void resetHeightCache(final JTree tree,
                                       final TreeCellRenderer defaultRenderer,
                                       final MultilineTreeCellRenderer renderer) {
    tree.setCellRenderer(defaultRenderer);
    tree.setCellRenderer(renderer);
  }

//  private static class DelegatingScrollablePanel extends JPanel implements Scrollable {
//    private final Scrollable myDelegatee;
//
//    public DelegatingScrollablePanel(Scrollable delegatee) {
//      super(new BorderLayout(0, 0));
//      myDelegatee = delegatee;
//      add((JComponent)delegatee, BorderLayout.CENTER);
//    }
//
//    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
//      return myDelegatee.getScrollableUnitIncrement(visibleRect, orientation, direction);
//    }
//
//    public boolean getScrollableTracksViewportWidth() {
//      return myDelegatee.getScrollableTracksViewportWidth();
//    }
//
//    public Dimension getPreferredScrollableViewportSize() {
//      return myDelegatee.getPreferredScrollableViewportSize();
//    }
//
//    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
//      return myDelegatee.getScrollableBlockIncrement(visibleRect, orientation, direction);
//    }
//
//    public boolean getScrollableTracksViewportHeight() {
//      return myDelegatee.getScrollableTracksViewportHeight();
//    }
//  }
}

