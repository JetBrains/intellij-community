package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.HTMLComposer;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefImplicitConstructor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;

public class Browser extends JPanel {
  private final ArrayList myClickListeners;
  private RefEntity myCurrentEntity;
  private final JEditorPane myHTMLViewer;
  private InspectionResultsView myView;

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
      myCurrentEntity = newEntity;
    }
    catch (Exception e) {
      showEmpty();
    }
  }

  public void showPageFor(RefElement refElement, ProblemDescriptor descriptor) {
    try {
      myCurrentEntity = refElement;
      String html = generateHTML(refElement, descriptor);
      myHTMLViewer.read(new StringReader(html), null);
      myHTMLViewer.setCaretPosition(0);
    }
    catch (Exception e) {
      showEmpty();
    }
  }

  public void showPageFor(RefEntity newEntity) {
    if (newEntity instanceof RefImplicitConstructor) {
      newEntity = ((RefImplicitConstructor)newEntity).getOwnerClass();
    }

    if (newEntity != myCurrentEntity) {
      showPageFromHistory(newEntity);
    }
  }

  public Browser(InspectionResultsView view) {
    super(new BorderLayout());
    myView = view;

    myClickListeners = new ArrayList();
    myCurrentEntity = null;

    myHTMLViewer = new JEditorPane("text/html", "<HTML><BODY>Select tree node for detailed information</BODY></HTML>");
    myHTMLViewer.setEditable(false);
    myHTMLViewer.addHyperlinkListener(new HyperlinkListener() {
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
                getTool().getQuickFixes()[actionNumber].doApplyFix(new RefElement[]{(RefElement)myCurrentEntity});
              }
              else if (ref.startsWith("invokelocal:")) {
                myView.invokeLocalFix();
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
    });

    add(new JScrollPane(myHTMLViewer), BorderLayout.CENTER);
  }

  public void addClickListener(ClickListener listener) {
    myClickListeners.add(listener);
  }

  private void fireClickEvent(VirtualFile file, int startPosition, int endPosition) {
    ClickEvent e = new ClickEvent(file, startPosition, endPosition);

    for (int i = 0; i < myClickListeners.size(); i++) {
      ClickListener listener = (ClickListener)myClickListeners.get(i);
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

    buf.insert(0, "<HTML><BODY><font style=\"font-family:verdana;\" size = \"3\">");
    buf.append("</font></BODY></HTML>");

    return buf.toString();
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

    buf.insert(0, "<HTML><BODY><font style=\"font-family:verdana;\" size = \"3\">");
    buf.append("</font></BODY></HTML>");

    return buf.toString();
  }

  private void uppercaseFirstLetter(final StringBuffer buf) {
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

  private InspectionTool getTool() { return myView.getSelectedTool(); }

  private HTMLComposer getComposer() { return getTool().getComposer(); }
}

