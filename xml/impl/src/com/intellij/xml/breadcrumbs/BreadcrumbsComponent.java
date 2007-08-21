/*
 * Copyright (c) 2007, Your Corporation. All Rights Reserved.
 */

package com.intellij.xml.breadcrumbs;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorFontType;
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
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * @author spleaner
 */
public class BreadcrumbsComponent extends JComponent implements Disposable {
  private Editor myEditor;
  private CrumbLine myLine;
  private List<LineElement> myCurrentList;
  private PsiFile myFile;
  private MergingUpdateQueue myQueue;
  private boolean myUserCaretChange = true;
  private BreadcrumbsInfoProvider myInfoProvider;
  private BreadcrumbsLoaderComponentImpl myLoaderComponent;

  public BreadcrumbsComponent(@NotNull final Editor editor, @NotNull final BreadcrumbsLoaderComponentImpl loaderComponent) {
    myEditor = editor;
    myLoaderComponent = loaderComponent;

    Document document = myEditor.getDocument();
    myFile = PsiDocumentManager.getInstance(myEditor.getProject()).getPsiFile(document);

    myInfoProvider = findInfoProvider(myFile, loaderComponent);

    final CaretListener caretListener = new CaretListener() {
      public void caretPositionChanged(final CaretEvent e) {
        if (myUserCaretChange) {
          myQueue.cancelAllUpdates();
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
        if (set instanceof XmlChangeSet && myQueue != null) {
          myQueue.cancelAllUpdates();
          myQueue.queue(new MyUpdate(BreadcrumbsComponent.this, editor));
        }
      }

      public boolean isAspectChangeInteresting(final PomModelAspect aspect) {
        return aspect instanceof XmlAspect; // PsiAspect???!
      }
    }, this);


    myLine = new CrumbLine(this);

    final Font editorFont = editor.getColorsScheme().getFont(EditorFontType.PLAIN);
    myLine.setFont(editorFont.deriveFont(Font.PLAIN, editorFont.getSize2D()));

    setLayout(new BorderLayout());

    add(myLine);

    final ComponentAdapter resizeListener = new ComponentAdapter() {
      public void componentResized(final ComponentEvent e) {
        myQueue.cancelAllUpdates();
        myQueue.queue(new MyUpdate(BreadcrumbsComponent.this, editor));
      }
    };

    myLine.addComponentListener(resizeListener);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        myLine.removeComponentListener(resizeListener);
      }
    });

    myQueue = new MergingUpdateQueue("Breadcrumbs.Queue", 200, true, this);
    myQueue.queue(new MyUpdate(this, editor));

    Disposer.register(this, new UiNotifyConnector(this, myQueue));
    Disposer.register(this, myQueue);
  }

  @Nullable
  BreadcrumbsInfoProvider findProviderForElement(@NotNull final PsiElement element) {
    return myLoaderComponent.getInfoProvider(element.getLanguage());
  }

  @Nullable
  private static BreadcrumbsInfoProvider findInfoProvider(@Nullable final PsiFile file,
                                                                          @NotNull BreadcrumbsLoaderComponentImpl loaderComponent) {
    BreadcrumbsInfoProvider provider = null;
    if (file != null) {
      final FileViewProvider viewProvider = file.getViewProvider();
      final Language baseLang = viewProvider.getBaseLanguage();
      provider = loaderComponent.getInfoProvider(baseLang);
      if (provider == null) {
        for (final Language language : viewProvider.getPrimaryLanguages()) {
          provider = loaderComponent.getInfoProvider(language);
          if (provider != null) {
            break;
          }
        }
      }
    }

    return provider;
  }

  private Editor getEditor() {
    return myEditor;
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

    //final PsiFile file = myFile.getViewProvider().getPsi(myLanguage);
    //return file != null ? file.getViewProvider().findElementAt(offset) : null;
    return myFile.getViewProvider().findElementAt(offset);
  }

  @Nullable
  private List<LineElement> getLineElements(@Nullable final PsiElement endElement) {
    if (endElement == null) {
      return null;
    }

    final LinkedList<LineElement> result = new LinkedList<LineElement>();

    PsiElement element = endElement;
    while (element != null) {
      BreadcrumbsInfoProvider provider = findProviderForElement(element);
      if (provider == null) {
        provider = myInfoProvider;
      }

      if (provider != null && provider.acceptElement(element)) {
        result.addFirst(new LineElement(element, provider));
      }

      element = element.getParent();
    }

    return result;
  }

  public void dispose() {
    myEditor = null;
    myCurrentList = null;
    myFile = null;
    myQueue = null;
    myLoaderComponent = null;
  }

  private void updateCrumbs(final LogicalPosition position) {
    if (myFile != null && myEditor != null) {
      if (PsiDocumentManager.getInstance(myFile.getProject()).isUncommited(myEditor.getDocument())) {
        return;
      }

      myCurrentList = getLineElements(getCaretElement(position));
      myLine.setCrumbs(myCurrentList);
    }
  }

  private static class LineElement {
    private BreadcrumbsInfoProvider myProvider;
    private PsiElement myElement;

    public LineElement(final PsiElement element, final BreadcrumbsInfoProvider provider) {
      myElement = element;
      myProvider = provider;
    }

    public BreadcrumbsInfoProvider getProvider() {
      return myProvider;
    }

    public boolean isValid() {
      return getPsiElement().isValid();
    }

    public PsiElement getPsiElement() {
      return myElement;
    }

    @NonNls
    public String getInfoString() {
      return isValid() ? myProvider.getElementInfo(getPsiElement()) : "?";
    }

    @Nullable
    public String getTooltipString() {
      return isValid() ? myProvider.getElementTooltip(getPsiElement()) : null;
    }
  }

  private static class CrumbLine extends JComponent {
    private List<LineElement> myElementList = new ArrayList<LineElement>();
    private Crumb myHovered;
    private PagedImage myBuffer;
    private List<Crumb> myCrumbs;
    private BreadcrumbsComponent myBreadcrumbsComponent;

    private static final Painter DEFAULT_PAINTER = new DefaultPainter(new ButtonSettings());
    private static final Painter TRANSPARENT_PAINTER = new DefaultPainter(new TransparentSettings());

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

    public BreadcrumbsComponent getBreadcrumbsComponent() {
      return myBreadcrumbsComponent;
    }

    @NotNull
    public Editor getEditor() {
      return myBreadcrumbsComponent.getEditor();
    }

    public void setCrumbs(@Nullable final List<LineElement> elementList) {
      if (myElementList != elementList) {
        myElementList = elementList;
        myCrumbs = null;
      }

      repaint();
    }

    @Nullable
    public Crumb getCrumb(@NotNull final Point p) {
      if (myCrumbs != null) {
        if (!getBounds().contains(p)) {
          return null;
        }

        if (myBuffer == null) {
          return null;
        }

        final int offset = myBuffer.getPageOffset();

        for (final Crumb each : myCrumbs) {
          if (((p.x + offset) >= each.getOffset()) && ((p.x + offset) < (each.getOffset() + each.getWidth()))) {
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

    public void paint(final Graphics g) {
      final Graphics2D g2 = ((Graphics2D)g);
      final Dimension d = getSize();
      final FontMetrics fm = g2.getFontMetrics();

      if (myElementList != null) {
        final boolean veryDirty = (myCrumbs == null) || (myBuffer != null && !myBuffer.isValid(d.width));

        final List<Crumb> crumbList = veryDirty ? createCrumbList(fm, myElementList, d.width) : myCrumbs;
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
      } else {
        super.paint(g2);
      }
    }

    private void setSelectedCrumb(@NotNull final Crumb c) {
      final LineElement selectedElement = c.getElement();

      final Set<PsiElement> elements = new HashSet<PsiElement>();
      boolean light = false;
      for (final Crumb each : myCrumbs) {
        final LineElement element = each.getElement();
        if (element != null && elements.contains(element.getPsiElement())) {
          light = false;
        }

        each.setLight(light);

        if (element != null && !light) {
          elements.add(element.getPsiElement());
        }

        if (selectedElement == element) {
          each.setSelected(true);
          light = true;
        }
        else {
          each.setSelected(false);
        }
      }

      repaint();
    }

    private void moveEditorCaretTo(@NotNull final PsiElement element) {
      if (element.isValid()) {
        myBreadcrumbsComponent.setUserCaretChange(false);
        getEditor().getCaretModel().moveToOffset(element.getTextOffset());
        getEditor().getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }
    }

    @Nullable
    private List<Crumb> createCrumbList(@NotNull final FontMetrics fm, @NotNull final List<LineElement> elements, final int width) {
      if (elements.size() == 0) {
        return null;
      }

      final LinkedList<Crumb> result = new LinkedList<Crumb>();
      int screenWidth = 0;
      Crumb rightmostCrumb = null;

      // fill up crumb list first going from end to start
      for (int i = elements.size() - 1; i >= 0; i--) {
        final NavigationCrumb forward = new NavigationCrumb(this, fm, true, DEFAULT_PAINTER);
        final NavigationCrumb backward = new NavigationCrumb(this, fm, false, DEFAULT_PAINTER);

        final LineElement element = elements.get(i);
        final String s = element.getInfoString();
        final Dimension d = DEFAULT_PAINTER.getSize(s, fm, width - forward.getWidth() - backward.getWidth());
        final Crumb crumb = new Crumb(this, s, d.width, element);
        if (screenWidth + d.width > width) {
          Crumb first = null;
          if (screenWidth + backward.getWidth() > width && !result.isEmpty()) {
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

      //assert screenWidth < width;

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

    private static int getTotalWidth(@NotNull final List<Crumb> crumbList) {
      int totalWidth = 0;
      for (final Crumb each : crumbList) {
        totalWidth += each.getWidth();
      }

      return totalWidth;
    }

    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    public Dimension getPreferredSize() {
      final Graphics2D g2 = (Graphics2D)getGraphics();
      return new Dimension(Integer.MAX_VALUE, DEFAULT_PAINTER.getSize("DUMMY", g2.getFontMetrics(), Integer.MAX_VALUE).height);
    }

    public Dimension getMaximumSize() {
      return getPreferredSize();
    }
  }

  private static class PagedImage {
    private int myPageWidth;
    private int myPage;
    private int myTotalWidth;

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
        myHoveredCrumb = crumb;
      }
    }

    public void mouseExited(final MouseEvent e) {
      mouseMoved(e);
    }

    public void mouseEntered(final MouseEvent e) {
      mouseMoved(e);
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
    private LineElement myElement;
    private CrumbLine myLine;
    private boolean mySelected;
    private boolean myHovered;
    private boolean myLight;

    public Crumb(final CrumbLine line, final String string, final int width, final LineElement element) {
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
      final LineElement element = getElement();
      if (element != null) {
        return element.getTooltipString();
      }

      return null;
    }

    public LineElement getElement() {
      return myElement;
    }

    public void performAction() {
      myLine.setSelectedCrumb(this);

      final PsiElement element = getElement().getPsiElement();
      if (element != null) {
        myLine.moveEditorCaretTo(element);
      }
    }

    public void setHovered(final boolean b) {
      myHovered = b;
    }
  }

  private static class NavigationCrumb extends Crumb {
    @NonNls private static final String FORWARD = ">>";
    @NonNls private static final String BACKWARD = "<<";
    private boolean myForward;
    private CrumbLine myLine;

    public NavigationCrumb(@NotNull final CrumbLine line, @NotNull final FontMetrics fm, final boolean forward, @NotNull final Painter p) {
      super(forward ? FORWARD : BACKWARD, p.getSize(forward ? FORWARD : BACKWARD, fm, Integer.MAX_VALUE).width);
      myForward = forward;
      myLine = line;
    }

    public void performAction() {
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

    public void paint(@NotNull final Graphics2D g2, @NotNull final Painter painter, final int height, final int pageOffset) {
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

  abstract static class PainterSettings {
    private static final Color DEFAULT_FOREGROUND_COLOR = new Color(50, 50, 50);

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

  private static class ButtonSettings extends PainterSettings {
    protected static final Color DEFAULT_BG_COLOR = new Color(245, 245, 245);
    private static final Color LIGHT_BG_COLOR = new Color(253, 253, 253);
    private static final Color CURRENT_BG_COLOR = new Color(250, 250, 220);
    protected static final Color HOVERED_BG_COLOR = new Color(220, 220, 220);

    private static final Color LIGHT_TEXT_COLOR = new Color(170, 170, 170);

    protected static final Color DEFAULT_BORDER_COLOR = new Color(90, 90, 90);
    private static final Color LIGHT_BORDER_COLOR = new Color(170, 170, 170);

    @Nullable
    Color getBackgroundColor(@NotNull final Crumb c) {
      if (c.isHovered()) {
        return HOVERED_BG_COLOR;
      }

      if (c.isSelected()) {
        return CURRENT_BG_COLOR;
      }

      if (c.isLight() && !(c instanceof NavigationCrumb)) {
        return LIGHT_BG_COLOR;
      }

      return DEFAULT_BG_COLOR;
    }

    @Nullable
    Color getForegroundColor(@NotNull final Crumb c) {
      if (c.isLight() && !c.isHovered() && !(c instanceof NavigationCrumb)) {
        return LIGHT_TEXT_COLOR;
      }

      return super.getForegroundColor(c);
    }

    @Nullable
    Color getBorderColor(@NotNull final Crumb c) {
      return (c.isLight() && !c.isHovered() && !(c instanceof NavigationCrumb)) ? LIGHT_BORDER_COLOR : DEFAULT_BORDER_COLOR;
    }
  }

  private static class TransparentSettings extends ButtonSettings {
    private static final Color LIGHT_TEXT_COLOR = new Color(130, 130, 130);

    @Nullable
    Color getBackgroundColor(@NotNull final Crumb c) {
      return c.isHovered() ? HOVERED_BG_COLOR : null;
    }

    @Nullable
    Color getBorderColor(@NotNull final Crumb c) {
      return c.isHovered() ? DEFAULT_BORDER_COLOR : null;
    }

    @Nullable
    Color getForegroundColor(@NotNull final Crumb c) {
      if (c.isLight() && !c.isHovered() && !(c instanceof NavigationCrumb)) {
        return LIGHT_TEXT_COLOR;
      }

      return super.getForegroundColor(c);
    }

    @Nullable
    Font getFont(@NotNull final Graphics g2, @NotNull final Crumb c) {
      if (c.isSelected()) {
        final Font font = g2.getFont();
        return font.deriveFont(Font.BOLD, font.getSize2D());
      }

      return null;
    }
  }

  abstract static class Painter {
    public static final int ROUND_VALUE = SystemInfo.isMac ? 3 : 2;

    private PainterSettings mySettings;

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

    public void paint(@NotNull final Crumb c, @NotNull final Graphics2D g2, final int height, final int pageOffset) {
      final PainterSettings s = getSettings();
      final Font oldFont = g2.getFont();
      final int offset = c.getOffset() - pageOffset;

      final Color bg = s.getBackgroundColor(c);
      final int width = c.getWidth();
      if (bg != null) {
        g2.setColor(bg);
        g2.fillRoundRect(offset + 2, 0, width - 4, height - 2, ROUND_VALUE, ROUND_VALUE);
      }

      final Color borderColor = s.getBorderColor(c);
      if (borderColor != null) {
        g2.setColor(borderColor);
        g2.drawRoundRect(offset + 1, 0, width - 2, height - 2, ROUND_VALUE, ROUND_VALUE);
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
      final StringBuffer sb = new StringBuffer();
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

      g2.drawString(string, offset + ROUND_VALUE, height - fm.getDescent() - 2);// fm.getHeight() - fm.getDescent() + (SystemInfo.isMac ? 0 : 0));

      g2.setFont(oldFont);
    }

    @NotNull
    Dimension getSize(@NotNull @NonNls final String s, @NotNull final FontMetrics fm, final int maxWidth) {
      final int width = fm.stringWidth(s) + (ROUND_VALUE * 2);
      return new Dimension(width > maxWidth ? maxWidth : width, fm.getHeight() + 2); //(SystemInfo.isMac ? 0 : 0));
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
