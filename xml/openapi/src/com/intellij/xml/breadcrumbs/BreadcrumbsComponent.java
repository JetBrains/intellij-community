/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.breadcrumbs;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.*;
import java.util.List;

/**
 * @author spleaner
 */
public class BreadcrumbsComponent<T extends BreadcrumbsItem> extends JComponent implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.breadcrumbs.BreadcrumbsComponent");
  private static final Painter DEFAULT_PAINTER = new DefaultPainter(new ButtonSettings());

  private static final int EXTRA_WIDTH = 10;

  private List<BreadcrumbsItemListener<T>> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private Crumb myHovered;
  private PagedImage myBuffer;
  private List<Crumb> myCrumbs = new ArrayList<Crumb>();
  private final CrumbLineMouseListener myMouseListener;
  private List<T> myItems;
  private int myOffset;

  public BreadcrumbsComponent() {
    myMouseListener = new CrumbLineMouseListener(this);
    addMouseListener(myMouseListener);
    addMouseMotionListener(myMouseListener);

    setToolTipText("");
  }

  public void setItems(@Nullable final List<T> itemsList) {
    if (myItems != itemsList) {
      myItems = itemsList;
      myCrumbs = null;
    }

    repaint();
  }

  public void setOffset(int offset) {
    if (myOffset != offset) {
      myOffset = offset;
      repaint();
    }
  }

  public void addBreadcrumbsItemListener(@NotNull final BreadcrumbsItemListener<T> listener) {
    myListeners.add(listener);
  }

  public void removeBreadcrumbsItemListener(@NotNull final BreadcrumbsItemListener<T> listener) {
    myListeners.remove(listener);
  }

  @Override
  public String getToolTipText(final MouseEvent event) {
    final Crumb c = getCrumb(event.getPoint());
    if (c != null) {
      final String text = c.getTooltipText();
      return text == null ? super.getToolTipText(event) : text;
    }

    return super.getToolTipText(event);
  }

  @Nullable
  public Crumb getCrumb(@NotNull final Point p) {
    if (myCrumbs != null) {
      final Rectangle r = getBounds();
      p.translate(r.x, r.y);
      
      if (!r.contains(p)) {
        return null;
      }

      if (myBuffer == null) {
        return null;
      }

      final int offset = myBuffer.getPageOffset();

      for (final Crumb each : myCrumbs) {
        if (p.x + offset >= each.getOffset() && p.x + offset < each.getOffset() + each.getWidth()) {
          return each;
        }
      }
    }

    return null;
  }

  public void setHoveredCrumb(@Nullable final Crumb crumb) {
    if (crumb != null) {
      crumb.setHovered(true);
    }

    if (myHovered != null) {
      myHovered.setHovered(false);
    }

    myHovered = crumb;
    for (BreadcrumbsItemListener listener : myListeners) {
      listener.itemHovered(myHovered != null ? myHovered.myItem : null);
    }
    repaint();
  }

  public void nextPage() {
    if (myBuffer != null) {
      final int page = myBuffer.getPage();
      if (page + 1 < myBuffer.getPageCount()) {
        myBuffer.setPage(page + 1);
      }
    }

    repaint();
  }

  public void previousPage() {
    if (myBuffer != null) {
      final int page = myBuffer.getPage();
      if (page - 1 >= 0) {
        myBuffer.setPage(page - 1);
      }
    }

    repaint();
  }

  @Override
  public void paint(final Graphics g) {
    final Graphics2D g2 = (Graphics2D)g;
    final Dimension d = getSize();
    final FontMetrics fm = g2.getFontMetrics();

    if (myItems != null) {
      final boolean veryDirty = myCrumbs == null || myBuffer != null && !myBuffer.isValid(d.width);

      final List<Crumb> crumbList = veryDirty ? createCrumbList(fm, myItems, d.width) : myCrumbs;
      if (crumbList != null) {
        if (veryDirty) {
          //final BufferedImage bufferedImage = createBuffer(crumbList, d.height);
          myBuffer = new PagedImage(getTotalWidth(crumbList), d.width);
          myBuffer.setPage(myBuffer.getPageCount() - 1); // point to the last page
        }

        assert myBuffer != null;

        super.paint(g2);

        //if (myDirty) {
        //  myBuffer.repaint(crumbList, getPainter());
        //myDirty = false;
        //}

        myBuffer.paintPage(g2, crumbList, DEFAULT_PAINTER, d.height);
        myCrumbs = crumbList;
      }
    }
    else {
      super.paint(g2);
    }
  }

  private void setSelectedCrumb(@NotNull final Crumb<T> c, final int modifiers) {
    final T selectedElement = c.getItem();

    final Set<BreadcrumbsItem> items = new HashSet<BreadcrumbsItem>();
    boolean light = false;
    for (final Crumb each : myCrumbs) {
      final BreadcrumbsItem item = each.getItem();
      if (item != null && items.contains(item)) {
        light = false;
      }

      each.setLight(light);

      if (item != null && !light) {
        items.add(item);
      }

      if (selectedElement == item) {
        each.setSelected(true);
        light = true;
      }
      else {
        each.setSelected(false);
      }
    }

    fireItemSelected(selectedElement, modifiers);

    repaint();
  }

  @SuppressWarnings({"ForLoopReplaceableByForEach"})
  private void fireItemSelected(@Nullable final T item, final int modifiers) {
    if (item != null) {
      for (BreadcrumbsItemListener listener : myListeners) {
        listener.itemSelected(item, modifiers);
      }
    }
  }

  @Nullable
  private List<Crumb> createCrumbList(@NotNull final FontMetrics fm, @NotNull final List<T> elements, final int width) {
    if (elements.size() == 0) {
      return null;
    }

    final LinkedList<Crumb> result = new LinkedList<Crumb>();
    int screenWidth = 0;
    Crumb rightmostCrumb = null;

    // fill up crumb list first going from end to start
    final NavigationCrumb fwd = new NavigationCrumb(this, fm, true, DEFAULT_PAINTER);
    for (int i = elements.size() - 1; i >= 0; i--) {
      final NavigationCrumb forward = new NavigationCrumb(this, fm, true, DEFAULT_PAINTER);
      final NavigationCrumb backward = new NavigationCrumb(this, fm, false, DEFAULT_PAINTER);
      final BreadcrumbsItem element = elements.get(i);
      final String s = element.getDisplayText();
      final Dimension d = DEFAULT_PAINTER.getSize(s, fm, width - forward.getWidth() - backward.getWidth());
      final Crumb crumb = new Crumb(this, s, d.width + EXTRA_WIDTH, element);
      if (screenWidth + d.width > width) {
        Crumb first = null;
        if (screenWidth + backward.getWidth() > width && !result.isEmpty()) {
          first = result.removeFirst();
          screenWidth -= first.getWidth();
        }

        // put backward crumb
        result.addFirst(backward);
        screenWidth += backward.getWidth() - myOffset;

        // put dummy crumb to fill up empty space (add it to the end!!!)
        int dummyWidth = width - screenWidth;
        if (dummyWidth > 0) {
          final DummyCrumb dummy = new DummyCrumb(dummyWidth);
          if (rightmostCrumb != null) {
            result.add(result.indexOf(rightmostCrumb) + 1, dummy);
          }
          else {
            result.addLast(dummy);
          }
        }

        // now add forward crumb
        screenWidth = forward.getWidth();
        result.addFirst(forward);

        if (first != null) {
          result.addFirst(first);
          screenWidth += first.getWidth();
        }

        rightmostCrumb = first != null ? first : crumb;
      }

      result.addFirst(crumb);

      screenWidth += d.width;
    }

    if (rightmostCrumb != null && screenWidth < width) {
      // add first dummy crumb
      result.add(result.indexOf(rightmostCrumb) + 2, new DummyCrumb(width - screenWidth - fwd.getWidth() - 8));
    }

    //assert screenWidth < width;

    // now fix up offsets going forward
    int offset = myOffset;
    for (final Crumb each : result) {
      each.setOffset(offset);
      offset += each.getWidth();
    }

    // set selected crumb
    if (result.size() > 0) {
      for (int i = result.size() - 1; i >= 0; i--) {
        final Crumb c = result.get(i);
        if (!(c instanceof DummyCrumb)) {
          c.setSelected(true);
          break;
        }
      }
    }

    return result;
  }

  private static int getTotalWidth(@NotNull final List<Crumb> crumbList) {
    int totalWidth = 0;
    for (final Crumb each : crumbList) {
      totalWidth += each.getWidth();
    }

    return totalWidth;
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getPreferredSize() {
    final Graphics2D g2 = (Graphics2D)getGraphics();
    return new Dimension(Integer.MAX_VALUE, g2 != null ? DEFAULT_PAINTER.getSize("DUMMY", g2.getFontMetrics(), Integer.MAX_VALUE).height + 1 : 1);
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  public void dispose() {
    removeMouseListener(myMouseListener);
    removeMouseMotionListener(myMouseListener);

    myListeners = null;
  }

  private static class PagedImage {
    private final int myPageWidth;
    private int myPage;
    private final int myTotalWidth;

    public PagedImage(int totalWidth, int pageWidth) {
      myPageWidth = pageWidth;
      myTotalWidth = totalWidth;
    }

    public int getPageCount() {
      if (myTotalWidth < myPageWidth) {
        return 1;
      }

      return myTotalWidth / myPageWidth;
    }

    public void setPage(final int page) {
      assert page >= 0;
      assert page < getPageCount();

      myPage = page;
    }

    public int getPage() {
      return myPage;
    }

    private void repaint(@NotNull final Graphics2D g2,
                         @NotNull final List<Crumb> crumbList,
                         @NotNull final Painter painter,
                         final int height) {
      UISettings.setupAntialiasing(g2);

      //final int height = myImage.getHeight();
      final int pageOffset = getPageOffset();

      for (final Crumb each : crumbList) {
        if (each.getOffset() >= pageOffset && each.getOffset() < pageOffset + myPageWidth) {
          each.paint(g2, painter, height, pageOffset);
        }
      }
    }

    public int getPageOffset() {
      return myPage * myPageWidth;
    }

    public void paintPage(@NotNull final Graphics2D g2, @NotNull final List<Crumb> list, @NotNull final Painter p, final int height) {
      repaint(g2, list, p, height);
    }

    public boolean isValid(final int width) {
      return width == myPageWidth;
    }
  }

  private static class CrumbLineMouseListener extends MouseAdapter implements MouseMotionListener {
    private final BreadcrumbsComponent myBreadcrumbs;
    private Crumb myHoveredCrumb;

    public CrumbLineMouseListener(@NotNull final BreadcrumbsComponent line) {
      myBreadcrumbs = line;
    }

    @Override
    public void mouseDragged(final MouseEvent e) {
      // nothing
    }

    @Override
    public void mouseMoved(final MouseEvent e) {
      final Crumb crumb = myBreadcrumbs.getCrumb(e.getPoint());
      if (crumb != myHoveredCrumb) {
        myBreadcrumbs.setHoveredCrumb(crumb);
        myHoveredCrumb = crumb;
      }
    }

    @Override
    public void mouseExited(final MouseEvent e) {
      mouseMoved(e);
    }

    @Override
    public void mouseEntered(final MouseEvent e) {
      mouseMoved(e);
    }

    @Override
    public void mouseClicked(final MouseEvent e) {
      final Crumb crumb = myBreadcrumbs.getCrumb(e.getPoint());
      if (crumb != null) {
        crumb.performAction(e.getModifiers());
      }
    }
  }

  private static class Crumb<T extends BreadcrumbsItem> {
    private final String myString;
    private int myOffset = -1;
    private final int myWidth;
    private T myItem;
    private BreadcrumbsComponent myLine;
    private boolean mySelected;
    private boolean myHovered;
    private boolean myLight;

    public Crumb(final BreadcrumbsComponent line, final String string, final int width, final T item) {
      this(string, width);

      myLine = line;
      myItem = item;
    }

    public Crumb(final String string, final int width) {
      myString = string;
      myWidth = width;
    }

    public String getString() {
      return myString;
    }

    public int getOffset() {
      LOG.assertTrue(myOffset != -1, "Negative offet for crumb: " + myString);
      return myOffset;
    }

    public int getWidth() {
      return myWidth;
    }

    public void setOffset(final int offset) {
      myOffset = offset;
    }

    public String toString() {
      return getString();
    }

    public void setSelected(final boolean selected) {
      mySelected = selected;
    }

    public void setLight(final boolean light) {
      myLight = light;
    }

    public boolean isHovered() {
      return myHovered;
    }

    public boolean isSelected() {
      return mySelected;
    }

    public boolean isLight() {
      return myLight;
    }

    public void paint(@NotNull final Graphics2D g2, @NotNull final Painter painter, final int height, final int pageOffset) {
      painter.paint(this, g2, height, pageOffset);
    }

    @Nullable
    public String getTooltipText() {
      final BreadcrumbsItem element = getItem();
      if (element != null) {
        return element.getTooltip();
      }

      return null;
    }

    public T getItem() {
      return myItem;
    }

    public void performAction(final int modifiers) {
      myLine.setSelectedCrumb(this, modifiers);
    }

    public void setHovered(final boolean b) {
      myHovered = b;
    }
  }

  private static class NavigationCrumb extends Crumb {
    @NonNls private static final String FORWARD = ">>";
    @NonNls private static final String BACKWARD = "<<";
    private final boolean myForward;
    private final BreadcrumbsComponent myLine;

    public NavigationCrumb(@NotNull final BreadcrumbsComponent line,
                           @NotNull final FontMetrics fm,
                           final boolean forward,
                           @NotNull final Painter p) {
      super(forward ? FORWARD : BACKWARD, p.getSize(forward ? FORWARD : BACKWARD, fm, Integer.MAX_VALUE).width + EXTRA_WIDTH);
      myForward = forward;
      myLine = line;
    }

    @Override
    public void performAction(final int modifiers) {
      if (myForward) {
        myLine.nextPage();
      }
      else {
        myLine.previousPage();
      }
    }
  }

  private static class DummyCrumb extends Crumb {
    public DummyCrumb(final int width) {
      super(null, width);
    }

    @Override
    public void paint(@NotNull final Graphics2D g2, @NotNull final Painter painter, final int height, final int pageOffset) {
      // does nothing
    }

    @Override
    public void performAction(final int modifiers) {
      // does nothing
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "DUMMY";
    }
  }

  abstract static class PainterSettings {
    private static final Color DEFAULT_FOREGROUND_COLOR = new JBColor(Gray._50, UIUtil.getListForeground());

    @Nullable
    Color getBackgroundColor(@NotNull final Crumb c) {
      return null;
    }

    @Nullable
    Color getForegroundColor(@NotNull final Crumb c) {
      return DEFAULT_FOREGROUND_COLOR;
    }

    @Nullable
    Color getBorderColor(@NotNull final Crumb c) {
      return null;
    }

    @Nullable
    Font getFont(@NotNull final Graphics g2, @NotNull final Crumb c) {
      return null;
    }
  }

  static class ButtonSettings extends PainterSettings {
    static final Color DEFAULT_BG_COLOR = new JBColor(Gray._245, new Color(101, 104, 106));
    private static final Color LIGHT_BG_COLOR = new JBColor(Gray._253, Gray._130);
    private static final Color CURRENT_BG_COLOR = new JBColor(new Color(250, 250, 220), new Color(97, 97, 75));
    private static final Color HOVERED_BG_COLOR = new JBColor(Gray._220, ColorUtil.shift(DEFAULT_BG_COLOR, 1.2));

    private static final Color LIGHT_TEXT_COLOR = new JBColor(Gray._170, UIUtil.getListForeground());

    private static final Color DEFAULT_BORDER_COLOR = new JBColor(Gray._170, Gray._50);
    private static final Color LIGHT_BORDER_COLOR = new JBColor(Gray._200, Gray._70);

    static Color getBackgroundColor(boolean selected, boolean hovered, boolean light, boolean navigationCrumb) {
      if (hovered) {
        return HOVERED_BG_COLOR;
      }

      if (selected) {
        return CURRENT_BG_COLOR;
      }

      if (light && !navigationCrumb) {
        return LIGHT_BG_COLOR;
      }

      return DEFAULT_BG_COLOR;
    }

    @Override
    @Nullable
    Color getBackgroundColor(@NotNull final Crumb c) {
      final BreadcrumbsItem item = c.getItem();
      if (item != null) {
        final CrumbPresentation presentation = item.getPresentation();
        if (presentation != null) {
          return presentation.getBackgroundColor(c.isSelected(), c.isHovered(), c.isLight());
        }
      }
      return getBackgroundColor(c.isSelected(), c.isHovered(), c.isLight(), c instanceof NavigationCrumb);
    }

    @Override
    @Nullable
    Color getForegroundColor(@NotNull final Crumb c) {
      if (c.isLight() && !c.isHovered() && !(c instanceof NavigationCrumb)) {
        return LIGHT_TEXT_COLOR;
      }

      return super.getForegroundColor(c);
    }

    @Override
    @Nullable
    Color getBorderColor(@NotNull final Crumb c) {
      return c.isLight() && !c.isHovered() && !(c instanceof NavigationCrumb) ? LIGHT_BORDER_COLOR : DEFAULT_BORDER_COLOR;
    }
  }

  abstract static class Painter {
    public static final int ROUND_VALUE = 2;

    private final PainterSettings mySettings;

    public Painter(@NotNull final PainterSettings s) {
      mySettings = s;
    }

    protected PainterSettings getSettings() {
      return mySettings;
    }

    abstract void paint(@NotNull final Crumb c, @NotNull final Graphics2D g2, final int height, final int pageOffset);

    @NotNull
    Dimension getSize(@NotNull @NonNls final String s, @NotNull final FontMetrics fm, final int maxWidth) {
      final int w = fm.stringWidth(s);
      return new Dimension(w > maxWidth ? maxWidth : w, fm.getHeight());
    }

  }

  private static class DefaultPainter extends Painter {
    public DefaultPainter(@NotNull final PainterSettings s) {
      super(s);
    }

    @Override
    public void paint(@NotNull final Crumb c, @NotNull final Graphics2D g2, final int height, final int pageOffset) {
      final PainterSettings s = getSettings();
      final Font oldFont = g2.getFont();
      final int offset = c.getOffset() - pageOffset;

      final Color bg = s.getBackgroundColor(c);
      final int width = c.getWidth();
      if (bg != null) {
        g2.setColor(bg);
        g2.fillRoundRect(offset + 2, 1, width - 4, height - 3, ROUND_VALUE, ROUND_VALUE);
      }

      final Color borderColor = s.getBorderColor(c);
      if (borderColor != null) {
        g2.setColor(borderColor);
        g2.drawRoundRect(offset + 2, 1, width - 4, height - 3, ROUND_VALUE, ROUND_VALUE);
      }

      final Color textColor = s.getForegroundColor(c);
      if (textColor != null) {
        g2.setColor(textColor);
      }

      final Font font = s.getFont(g2, c);
      if (font != null) {
        g2.setFont(font);
      }

      final FontMetrics fm = g2.getFontMetrics();

      String string = c.getString();
      if (fm.stringWidth(string) > width) {
        final int dotsWidth = fm.stringWidth("...");
        final StringBuilder sb = new StringBuilder();
        int length = 0;
        for (int i = 0; i < string.length(); i++) {
          final int charWidth = fm.charWidth(string.charAt(i));
          if (length + charWidth + dotsWidth > width) {
            break;
          }

          length += charWidth;
          sb.append(string.charAt(i));
        }

        string = sb.append("...").toString();
      }

      g2.drawString(string, offset + ROUND_VALUE + 5, height - fm.getDescent() - 3);

      g2.setFont(oldFont);
    }

    @Override
    @NotNull
    Dimension getSize(@NotNull @NonNls final String s, @NotNull final FontMetrics fm, final int maxWidth) {
      final int width = fm.stringWidth(s) + ROUND_VALUE * 2;
      return new Dimension(width > maxWidth ? maxWidth : width, fm.getHeight() + 4);
    }
  }
}
