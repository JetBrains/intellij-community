/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.xml.breadcrumbs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomChangeSet;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.pom.xml.XmlAspect;
import com.intellij.pom.xml.XmlChangeSet;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.*;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * @author spleaner
 */
public class BreadcrumbsComponent extends JComponent implements Disposable {
  @NonNls private static final String CLASS_ATTRIBUTE_NAME = "class";
  @NonNls private static final String ID_ATTRIBUTE_NAME = "id";

  private static final Color DEFAULT_BG_COLOR = new Color(245, 245, 245);
  private static final Color LIGHT_BG_COLOR = new Color(253, 253, 253);
  private static final Color CURRENT_BG_COLOR = new Color(250, 250, 220);
  private static final Color HOVERED_BG_COLOR = new Color(220, 220, 220);

  private static final Color DEFAULT_TEXT_COLOR = new Color(50, 50, 50);
  private static final Color LIGHT_TEXT_COLOR = new Color(170, 170, 170);

  private static final Color DEFAULT_BORDER_COLOR = new Color(90, 90, 90);
  private static final Color LIGHT_BORDER_COLOR = new Color(170, 170, 170);

  private Editor myEditor;
  private CrumbLine myLine;
  private LinkedList<XmlElement> myCurrentList;
  private PsiFile myFile;
  private MergingUpdateQueue myQueue;
  private boolean myUserCaretChange = true;

  public BreadcrumbsComponent(@NotNull final Editor editor) {
    myEditor = editor;

    Document document = myEditor.getDocument();
    myFile = PsiDocumentManager.getInstance(myEditor.getProject()).getPsiFile(document);

    final CaretListener caretListener = new CaretListener() {
      public void caretPositionChanged(final CaretEvent e) {
        if (myUserCaretChange) {
          myQueue.queue(new MyUpdate(BreadcrumbsComponent.this, editor));
        }

        myUserCaretChange = true;
      }
    };

    editor.getCaretModel().addCaretListener(caretListener);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        editor.getCaretModel().removeCaretListener(caretListener);
      }
    });


    final Project project = editor.getProject();
    assert project != null;

    project.getModel().addModelListener(new PomModelListener() {
      public void modelChanged(final PomModelEvent event) {
        final PomChangeSet set = event.getChangeSet(event.getSource().getModelAspect(XmlAspect.class));
        if (set instanceof XmlChangeSet) {
          myQueue.queue(new MyUpdate(BreadcrumbsComponent.this, editor));
        }
      }

      public boolean isAspectChangeInteresting(final PomModelAspect aspect) {
        return aspect instanceof XmlAspect;
      }
    }, this);

    myLine = new CrumbLine(this);
    setLayout(new BorderLayout());

    add(myLine);

    myQueue = new MergingUpdateQueue("Breadcrumbs.Queue", 200, true, this);
    myQueue.queue(new MyUpdate(this, editor));

    Disposer.register(this, new UiNotifyConnector(this, myQueue));
    Disposer.register(this, myQueue);
  }

  private Editor getEditor() {
    return myEditor;
  }

  public String getToolTipText(final MouseEvent event) {
    return super.getToolTipText(event);    //To change body of overridden methods use File | Settings | File Templates.
  }

  private void setUserCaretChange(final boolean userCaretChange) {
    myUserCaretChange = userCaretChange;
  }

  @Nullable
  private PsiElement getCaretElement(@NotNull final LogicalPosition position) {
    if (myFile == null) {
      return null;
    }

    final int offset = myEditor.logicalPositionToOffset(position);

    final XmlDocument xmlDocument = ((XmlFile)myFile).getDocument();
    assert xmlDocument != null;
    return xmlDocument.findElementAt(offset);
  }

  @NotNull
  private List<XmlElement> getLineElements(@NotNull final PsiElement endElement) {
    final LinkedList<XmlElement> result = new LinkedList<XmlElement>();

    PsiElement element = endElement;
    while (element != null) {
      if (element instanceof XmlTag) {
        result.addFirst((XmlElement)element);
      }

      element = element.getParent();
    }

    myCurrentList = result;

    return myCurrentList;
  }

  public void dispose() {
    myEditor = null;
    myCurrentList = null;
    myFile = null;
    myQueue = null;
  }

  private void updateCrumbs(final LogicalPosition positon) {
    if (PsiDocumentManager.getInstance(myFile.getProject()).isUncommited(myEditor.getDocument())) {
      return;
    }

    final PsiElement element = getCaretElement(positon);
    if (element != null) {
      myLine.setCrumbs(getLineElements(element));
    }
  }

  private static class CrumbLine extends JComponent {
    private List<XmlElement> myElementList = new ArrayList<XmlElement>();
    private Crumb myHovered;
    private boolean myDirty = true;
    private BufferedImage myBuffer;
    private int myBufferOffset;
    private List<Crumb> myCrumbs;
    private BreadcrumbsComponent myBreadcrumbsComponent;

    public CrumbLine(@NotNull final BreadcrumbsComponent breadcrumbsComponent) {
      final CrumbLineMouseListener listener = new CrumbLineMouseListener(this);
      addMouseListener(listener);
      addMouseMotionListener(listener);

      myBreadcrumbsComponent = breadcrumbsComponent;

      setToolTipText(new String());
    }

    public String getToolTipText(final MouseEvent event) {
      final Crumb c = getCrumb(event.getPoint());
      if (c != null) {
        final String text = c.getTooltipText();
        return text == null ? super.getToolTipText(event) : text;
      }

      return super.getToolTipText(event);
    }

    @NotNull
    public Editor getEditor() {
      return myBreadcrumbsComponent.getEditor();
    }

    public void setCrumbs(@NotNull final List<XmlElement> elementList) {
      if (myElementList != elementList) {
        myElementList = elementList;
        myCrumbs = null;
        myBufferOffset = 0;
      }

      myDirty = true;
      repaint();
    }

    @NotNull
    public List<XmlElement> getCrumbs() {
      return myElementList;
    }

    @Nullable
    public Crumb getCrumb(@NotNull final Point p) {
      if (myCrumbs != null) {
        for (final Crumb each : myCrumbs) {
          if ((p.x + myBufferOffset >= each.getOffset()) && (p.x + myBufferOffset < each.getOffset() + each.getWidth())) {
            return each;
          }
        }
      }

      return null;
    }

    @NotNull
    private static String prepareString(@NotNull XmlTag tag) {
      final StringBuffer sb = new StringBuffer();
      sb.append(tag.getName());

      final String id_value = tag.getAttributeValue(ID_ATTRIBUTE_NAME);
      if (null != id_value) {
        sb.append(" ").append("#").append(id_value);
      }

      final String class_value = tag.getAttributeValue(CLASS_ATTRIBUTE_NAME);
      if (null != class_value) {
        final StringTokenizer tokenizer = new StringTokenizer(class_value, " ");
        while (tokenizer.hasMoreTokens()) {
          sb.append(" .").append(tokenizer.nextToken());
        }
      }

      return sb.toString();
    }

    public void setHoveredCrumb(@Nullable final Crumb crumb) {
      if (crumb != null) {
        crumb.setHovered(true);
      }

      if (myHovered != null) {
        myHovered.setHovered(false);
      }

      myHovered = crumb;
      myDirty = true;
      repaint();
    }

    public void paint(final Graphics g) {
      final Graphics2D g2 = ((Graphics2D)g);
      final Dimension d = getSize();

      final FontMetrics fm = g2.getFontMetrics();

      boolean veryDirty = (myCrumbs == null);
      final List<Crumb> crumbList = veryDirty ? createCrumbList(fm, myElementList, d.width) : myCrumbs;
      if (crumbList != null) {
        if (myDirty) { // TODO[spLeaner]: make buffer operations faster by redrawing only changed crumbs!!!
          myBuffer = createBuffer(this, crumbList, d.height);
          myDirty = false;
        }

        BufferedImage image2draw = myBuffer;
        if (myBuffer.getWidth() > d.width) {
          if (veryDirty) {
            // reset buffer offset to point to the last page
            myBufferOffset = myBuffer.getWidth() - d.width;
          }

          int subSize = d.width;
          int offset = myBufferOffset;
          if (myBuffer.getWidth() < d.width) {
            subSize = myBuffer.getWidth();
          }
          else if (myBufferOffset < 0) {
            subSize = d.width + myBufferOffset;
            offset = 0;
          }

          image2draw = myBuffer.getSubimage(offset, 0, subSize, d.height);
        }

        g2.drawImage(image2draw, myBufferOffset < 0 ? Math.abs(myBufferOffset) : 0, 0, this);
        myCrumbs = crumbList;
      }
    }

    private void forward() {
      myBufferOffset += getSize().width;
      repaint();
    }

    private void backward() {
      myBufferOffset -= getSize().width;
      repaint();
    }

    private void setSelectedCrumb(@NotNull final Crumb c) {
      final XmlElement selectedElement = c.getElement();

      final Set<XmlElement> elements = new HashSet<XmlElement>();
      boolean light = false;
      for (final Crumb each : myCrumbs) {
        if (elements.contains(each.getElement())) {
          light = false;
        }

        each.setLight(light);

        if (!light) {
          elements.add(each.getElement());
        }

        if (selectedElement == each.getElement()) {
          each.setSelected(true);
          light = true;
        }
        else {
          each.setSelected(false);
        }
      }

      myDirty = true;
      repaint();
    }

    private void moveEditorCaretTo(@NotNull final XmlElement element) {
      assert element.isValid();
      myBreadcrumbsComponent.setUserCaretChange(false);
      getEditor().getCaretModel().moveToOffset(element.getTextOffset());
      getEditor().getScrollingModel().scrollToCaret(ScrollType.CENTER);
    }

    @Nullable
    private List<Crumb> createCrumbList(@NotNull final FontMetrics fm, @NotNull final List<XmlElement> elements, final int width) {
      if (elements.size() == 0) {
        return null;
      }

      final LinkedList<Crumb> result = new LinkedList<Crumb>();
      int screenWidth = 0;
      Crumb rightmostCrumb = null;

      // fill up crumb list first going from end to start
      for (int i = elements.size() - 1; i >= 0; i--) {
        final XmlTag tag = (XmlTag)elements.get(i);
        final String s = prepareString(tag);
        final Dimension d = CrumbPainter.getSize(s, fm);
        final Crumb crumb = new Crumb(this, s, d.width, tag);
        if (screenWidth + d.width > width) {
          final NavigationCrumb forward = new NavigationCrumb(this, fm, true);
          final NavigationCrumb backward = new NavigationCrumb(this, fm, false);

          Crumb first = null;
          if (screenWidth + backward.getWidth() > width) {
            first = result.removeFirst();
            screenWidth -= first.getWidth();
          }

          // put backward crumb
          result.addFirst(backward);
          screenWidth += backward.getWidth();

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

          rightmostCrumb = (first != null) ? first : crumb;
        }

        result.addFirst(crumb);

        screenWidth += d.width;
      }

      if (rightmostCrumb != null && screenWidth < width) {
        // fill up empty space with elements from the full screen
        int index = result.indexOf(rightmostCrumb);
        for (int i = index + 1; i < result.size(); i++) {
          final Crumb crumb = result.get(i);
          if (crumb instanceof NavigationCrumb || crumb instanceof DummyCrumb) {
            continue;
          }

          if (screenWidth + crumb.getWidth() < width) {
            result.add(++index, new Crumb(this, crumb.getString(), crumb.getWidth(), crumb.getElement()));
            screenWidth += crumb.getWidth();
            i++;
          }
          else {
            break;
          }
        }

        // add first dummy crumb
        if (screenWidth < width) {
          result.add(index + 1, new DummyCrumb(width - screenWidth));
        }
      }

      assert screenWidth < width;

      // now fix up offsets going forward
      int offset = 0;
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

    @NotNull
    private static BufferedImage createBuffer(@NotNull final JComponent parent, @NotNull final List<Crumb> crumbList, final int height) {
      int totalWidth = 0;
      for (final Crumb each : crumbList) {
        totalWidth += each.getWidth();
      }

      final BufferedImage result = new BufferedImage(totalWidth, height, BufferedImage.TYPE_INT_ARGB);
      final Graphics2D g2 = (Graphics2D)result.getGraphics();
      g2.setFont(parent.getFont());

      for (final Crumb each : crumbList) {
        each.paint(g2, height);
      }

      return result;
    }

    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    public Dimension getPreferredSize() {
      final Graphics2D g2 = (Graphics2D)getGraphics();
      return new Dimension(Integer.MAX_VALUE, CrumbPainter.getSize("dummy", g2.getFontMetrics()).height);
    }

    public Dimension getMaximumSize() {
      return getPreferredSize();
    }
  }

  private static class CrumbLineMouseListener extends MouseAdapter implements MouseMotionListener {
    private CrumbLine myLine;
    private Crumb myHoveredCrumb;

    public CrumbLineMouseListener(@NotNull final CrumbLine line) {
      myLine = line;
    }

    public void mouseDragged(final MouseEvent e) {
      // nothing
    }

    public void mouseMoved(final MouseEvent e) {
      final Crumb crumb = myLine.getCrumb(e.getPoint());
      if (crumb != myHoveredCrumb) {
        myLine.setHoveredCrumb(crumb);
        myLine.repaint();
        myHoveredCrumb = crumb;
      }
    }

    public void mouseExited(final MouseEvent e) {
      myLine.setHoveredCrumb(null);
    }

    public void mouseClicked(final MouseEvent e) {
      final Crumb crumb = myLine.getCrumb(e.getPoint());
      if (crumb != null) {
        crumb.performAction();
      }
    }
  }

  private static class Crumb {
    private String myString;
    private int myOffset = -1;
    private int myWidth;
    private XmlElement myElement;
    private CrumbLine myLine;
    private boolean mySelected;
    private boolean myHovered;
    private boolean myLight;

    public Crumb(final CrumbLine line, final String string, final int width, final XmlElement element) {
      this(string, width);

      myLine = line;
      myElement = element;
    }

    public Crumb(final String string, final int width) {
      myString = string;
      myWidth = width;
    }

    public String getString() {
      return myString;
    }

    public int getOffset() {
      assert myOffset != -1;
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

    @Nullable
    public String getTooltipText() {
      final XmlElement element = getElement();
      if (element != null) {
        if (element instanceof XmlTag) {
          final XmlTag tag = (XmlTag)element;
          final StringBuffer result = new StringBuffer("<");
          result.append(tag.getName());
          final XmlAttribute[] attributes = tag.getAttributes();
          for (final XmlAttribute each : attributes) {
            result.append(" ").append(each.getText());
          }

          if (tag.isEmpty()) {
            result.append("/>");
          }
          else {
            result.append(">...</").append(tag.getName()).append(">");
          }

          return result.toString();
        }
      }

      return null;
    }

    public void paint(@NotNull final Graphics2D g2, int height) {
      final Color bg = myHovered
                       ? BreadcrumbsComponent.HOVERED_BG_COLOR
                       : myLight
                         ? BreadcrumbsComponent.LIGHT_BG_COLOR
                         : mySelected ? BreadcrumbsComponent.CURRENT_BG_COLOR : BreadcrumbsComponent.DEFAULT_BG_COLOR;

      final Color text = myHovered
                         ? BreadcrumbsComponent.DEFAULT_TEXT_COLOR
                         : myLight ? BreadcrumbsComponent.LIGHT_TEXT_COLOR : BreadcrumbsComponent.DEFAULT_TEXT_COLOR;

      final Color border = myHovered
                           ? BreadcrumbsComponent.DEFAULT_BORDER_COLOR
                           : myLight ? BreadcrumbsComponent.LIGHT_BORDER_COLOR : BreadcrumbsComponent.DEFAULT_BORDER_COLOR;

      CrumbPainter.paint(this, height, g2, text, bg, border);
    }

    public XmlElement getElement() {
      return myElement;
    }

    public void performAction() {
      myLine.setSelectedCrumb(this);

      final XmlElement element = getElement();
      if (element != null) {
        myLine.moveEditorCaretTo(element);
      }
    }

    public void setHovered(final boolean b) {
      myHovered = b;
    }
  }

  private static class NavigationCrumb extends Crumb {
    @NonNls private static final String TEXT = "...";
    private boolean myForward;
    private CrumbLine myLine;

    public NavigationCrumb(@NotNull final CrumbLine line, @NotNull final FontMetrics fm, final boolean forward) {
      super(TEXT, CrumbPainter.getSize(TEXT, fm).width);
      myForward = forward;
      myLine = line;
    }

    public void performAction() {
      if (myForward) {
        myLine.forward();
      }
      else {
        myLine.backward();
      }
    }

    public void paint(@NotNull final Graphics2D g2, final int height) {
      final Color text = BreadcrumbsComponent.DEFAULT_TEXT_COLOR;
      final Color bg = isHovered() ? BreadcrumbsComponent.HOVERED_BG_COLOR : BreadcrumbsComponent.DEFAULT_BG_COLOR;
      final Color border = BreadcrumbsComponent.DEFAULT_BORDER_COLOR;

      CrumbPainter.paint(this, height, g2, text, bg, border);
    }
  }

  private static class DummyCrumb extends Crumb {
    public DummyCrumb(final int width) {
      super(null, width);
    }

    public void paint(@NotNull final Graphics2D g2, final int height) {
      // does nothing
    }

    public void performAction() {
      // does nothing
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    public String toString() {
      return "DUMMY";
    }
  }

  @SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
  private static class CrumbPainter {
    public static void paint(@NotNull final Crumb c,
                             final int height,
                             @NotNull final Graphics2D g2,
                             final Color textColor,
                             final Color bg,
                             final Color border) {

      int roundValue = SystemInfo.isMac ? 5 : 2;

      g2.setColor(bg);
      g2.fillRoundRect(c.getOffset() + 2, 2, c.getWidth() - 4, height - 4, roundValue, roundValue);

      g2.setColor(border);
      g2.drawRoundRect(c.getOffset() + 2, 2, c.getWidth() - 4, height - 4, roundValue, roundValue);

      g2.setColor(textColor);
      g2.drawString(c.getString(), c.getOffset() + 4, 2 + g2.getFontMetrics().getAscent());
    }

    @NotNull
    public static Dimension getSize(@NonNls @NotNull final String s, @NotNull final FontMetrics fm) {
      return new Dimension(fm.stringWidth(s) + 8, fm.getHeight() + 4);
    }
  }

  private class MyUpdate extends Update {
    private BreadcrumbsComponent myBreadcrumbsComponent;
    private Editor myEditor;

    public MyUpdate(@NonNls final BreadcrumbsComponent c, @NotNull final Editor editor) {
      super(c);

      myBreadcrumbsComponent = c;
      myEditor = editor;
    }

    public void run() {
      myBreadcrumbsComponent.updateCrumbs(myEditor.getCaretModel().getLogicalPosition());
    }

    public boolean canEat(final Update update) {
      return true;
    }
  }

}
