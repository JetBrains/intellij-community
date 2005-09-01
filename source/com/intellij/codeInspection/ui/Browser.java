package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.HTMLComposer;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefImplicitConstructor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Browser extends JPanel {
  private static final String UNDER_CONSTRUCTION = "Under construction";
  private final List<ClickListener> myClickListeners;
  private RefEntity myCurrentEntity;
  private JEditorPane myHTMLViewer;
  private InspectionResultsView myView;
  private HyperlinkListener myHyperLinkListener;

  public void dispose() {
    removeAll();
    myHTMLViewer.removeHyperlinkListener(myHyperLinkListener);
    myClickListeners.clear();
    myHTMLViewer = null;
  }

  public static class ClickEvent {
    public static final int REF_ELEMENT = 1;
    public static final int FILE_OFFSET = 2;
    private final VirtualFile myFile;
    private final int myStartPosition;
    private final int myEndPosition;
    private final RefElement refElement;
    private final int myEventType;

    public ClickEvent(VirtualFile myFile, int myStartPosition, int myEndPosition) {
      this.myFile = myFile;
      this.myStartPosition = myStartPosition;
      this.myEndPosition = myEndPosition;
      myEventType = FILE_OFFSET;
      refElement = null;
    }

    public int getEventType() {
      return myEventType;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public int getStartOffset() {
      return myStartPosition;
    }

    public int getEndOffset() {
      return myEndPosition;
    }

    public RefElement getClickedElement() {
      return refElement;
    }

  }

  public interface ClickListener {
    void referenceClicked(ClickEvent e);
  }

  private void showPageFromHistory(RefEntity newEntity) {
    try {
      String html = generateHTML(newEntity);
      myHTMLViewer.read(new StringReader(html), null);
      myHTMLViewer.setCaretPosition(0);
    }
    catch (Exception e) {
      showEmpty();
    }
    finally {
      myCurrentEntity = newEntity;
    }
  }

  public void showPageFor(RefElement refElement, ProblemDescriptor descriptor) {
    try {
      String html = generateHTML(refElement, descriptor);
      myHTMLViewer.read(new StringReader(html), null);
      myHTMLViewer.setCaretPosition(0);
    }
    catch (Exception e) {
      showEmpty();
    }
    finally {
      myCurrentEntity = refElement;
    }
  }

  public void showPageFor(RefEntity newEntity) {
    if (newEntity instanceof RefImplicitConstructor) {
      newEntity = ((RefImplicitConstructor)newEntity).getOwnerClass();
    }

    //multiple problems for one entity -> refresh browser
    showPageFromHistory(newEntity);
  }

  public Browser(InspectionResultsView view) {
    super(new BorderLayout());
    myView = view;

    myClickListeners = new ArrayList<ClickListener>();
    myCurrentEntity = null;

    myHTMLViewer = new JEditorPane("text/html", "<HTML><BODY>Select tree node for detailed information</BODY></HTML>");
    myHTMLViewer.setEditable(false);
    myHyperLinkListener = new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          JEditorPane pane = (JEditorPane)e.getSource();
          if (e instanceof HTMLFrameHyperlinkEvent) {
            HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
            HTMLDocument doc = (HTMLDocument)pane.getDocument();
            doc.processHTMLFrameHyperlinkEvent(evt);
          }
          else {
            try {
              URL url = e.getURL();
              String ref = url.getRef();
              if (ref.startsWith("pos:")) {
                int delimeterPos = ref.indexOf(':', "pos:".length() + 1);
                String startPosition = ref.substring("pos:".length(), delimeterPos);
                String endPosition = ref.substring(delimeterPos + 1);
                Integer textStartOffset = new Integer(startPosition);
                Integer textEndOffset = new Integer(endPosition);
                String fileURL = url.toExternalForm();
                fileURL = fileURL.substring(0, fileURL.indexOf('#'));
                VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(fileURL);
                if (vFile != null) {
                  fireClickEvent(vFile, textStartOffset.intValue(), textEndOffset.intValue());
                }
              }
              else if (ref.startsWith("descr:")) {
                int descriptionIndex = Integer.parseInt(ref.substring("descr:".length()));
                ProblemDescriptor descriptor = ((DescriptorProviderInspection)getTool()).getDescriptions(
                  (RefElement)myCurrentEntity)[descriptionIndex];
                PsiElement psiElement = descriptor.getPsiElement();
                if (psiElement == null) return;
                VirtualFile vFile = psiElement.getContainingFile().getVirtualFile();
                if (vFile != null) {
                  TextRange range = psiElement.getTextRange();
                  fireClickEvent(vFile, range.getStartOffset(), range.getEndOffset());
                }
              }
              else if (ref.startsWith("invoke:")) {
                int actionNumber = Integer.parseInt(ref.substring("invoke:".length()));
                getTool().getQuickFixes(new RefElement[]{(RefElement)myCurrentEntity})[actionNumber]
                  .doApplyFix(new RefElement[]{(RefElement)myCurrentEntity});
              }
              else if (ref.startsWith("invokelocal:")) {
                int actionNumber = Integer.parseInt(ref.substring("invokelocal:".length()));
                if (actionNumber > -1) {
                  myView.invokeLocalFix(actionNumber);
                }
              }
              else {
                int offset = Integer.parseInt(ref);
                String fileURL = url.toExternalForm();
                fileURL = fileURL.substring(0, fileURL.indexOf('#'));
                VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(fileURL);
                if (vFile != null) {
                  fireClickEvent(vFile, offset, offset);
                }
              }
            }
            catch (Throwable t) {
              t.printStackTrace();
            }
          }
        }
      }
    };
    myHTMLViewer.addHyperlinkListener(myHyperLinkListener);

    add(new JScrollPane(myHTMLViewer), BorderLayout.CENTER);
  }

  public void addClickListener(ClickListener listener) {
    myClickListeners.add(listener);
  }

  private void fireClickEvent(VirtualFile file, int startPosition, int endPosition) {
    ClickEvent e = new ClickEvent(file, startPosition, endPosition);

    for (ClickListener listener : myClickListeners) {
      listener.referenceClicked(e);
    }
  }

  private String generateHTML(final RefEntity refEntity) {
    final StringBuffer buf = new StringBuffer();
    if (refEntity instanceof RefElement) {
      final Runnable action = new Runnable() {
        public void run() {
          getComposer().compose(buf, refEntity);
        }
      };
      ApplicationManager.getApplication().runReadAction(action);
    }
    else {
      getComposer().compose(buf, refEntity);
    }

    uppercaseFirstLetter(buf);

    insertHeaderFooter(buf);

    return buf.toString();
  }

  private static void insertHeaderFooter(final StringBuffer buf) {
    buf.insert(0, "<HTML><BODY><font style=\"font-family:verdana;\" size = \"3\">");
    buf.append("</font></BODY></HTML>");
  }

  private String generateHTML(final RefElement refElement, final ProblemDescriptor descriptor) {
    final StringBuffer buf = new StringBuffer();
    final Runnable action = new Runnable() {
      public void run() {
        getComposer().compose(buf, refElement, descriptor);
      }
    };
    ApplicationManager.getApplication().runReadAction(action);

    uppercaseFirstLetter(buf);

    insertHeaderFooter(buf);

    return buf.toString();
  }

  private static void uppercaseFirstLetter(final StringBuffer buf) {
    if (buf.length() > 1) {
      char[] firstLetter = new char[1];
      buf.getChars(0, 1, firstLetter, 0);
      buf.setCharAt(0, Character.toUpperCase(firstLetter[0]));
    }
  }

  public void showEmpty() {
    myCurrentEntity = null;
    try {
      myHTMLViewer.read(new StringReader("<html><body></body></html>"), null);
    }
    catch (IOException e) {
    }
  }

  public void showDescription(InspectionTool tool){
    if (tool.getShortName().length() == 0){
      showEmpty();
      return;
    }
    StringBuffer page = new StringBuffer("<html><title>");
    HTMLComposer.appendHeading(page, "ID");
    page.append("</title>&nbsp;&nbsp;<br>");
    page.append(tool.getShortName());
    page.append("<br>");
    page.append("<title><br><font style=\"font-family:verdana; font-weight:bold; color:#005555\"; size = \"3\">Description:</font></title><br>");
    final URL descriptionUrl = getDescriptionUrl(tool);
    final String underConstruction = "<b>" + UNDER_CONSTRUCTION + "</b></html>";
    try {
      if (descriptionUrl != null){
        final StringBuffer description = readInputStream(descriptionUrl.openStream());
        if (description != null && description.toString().startsWith("<html>")) {
          page.append(description.substring(description.indexOf("<html>") + 6));
        } else {
          page.append(underConstruction);
        }
      } else {
        page.append(underConstruction);
      }
      myHTMLViewer.setText(page.toString());
    }
    catch (IOException e) {
      try {
        myHTMLViewer.read(new StringReader(page.append(underConstruction).toString()), null);
      }
      catch (IOException e1) {
        //Can't be
      }
    } finally {
      myCurrentEntity = null;
    }
  }

  @Nullable
  private static StringBuffer readInputStream(InputStream in) {
    try {
      StringBuffer str = new StringBuffer();
      int c = in.read();
      while (c != -1) {
        str.append((char)c);
        c = in.read();
      }
      return str;
    }
    catch (IOException e) {
      return null;
    }
  }

  private URL getDescriptionUrl(InspectionTool tool) {
    Class aClass = tool instanceof LocalInspectionToolWrapper ? ((LocalInspectionToolWrapper)tool).getTool().getClass() : tool.getClass();
    return aClass.getResource("/inspectionDescriptions/" + tool.getDescriptionFileName());
  }

  @Nullable
  private InspectionTool getTool() {
    if (myView != null){
      return myView.getSelectedTool();
    }
    return null;
  }

  @Nullable
  private HTMLComposer getComposer() {
    final InspectionTool tool = getTool();
    if (tool != null){
      return tool.getComposer();
    }
    return null;
  }
}

