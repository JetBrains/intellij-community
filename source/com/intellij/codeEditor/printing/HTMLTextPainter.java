package com.intellij.codeEditor.printing;

import com.intellij.codeInsight.daemon.impl.GeneralHighlightingPass;
import com.intellij.codeInsight.daemon.impl.LineMarkerInfo;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorHighlighter;
import com.intellij.openapi.editor.ex.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

class HTMLTextPainter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeEditor.printing.HTMLTextPainter");

  private int myOffset = 0;
  private EditorHighlighter myHighlighter;
  private String myText;
  private String myFileName;
  private String myHTMLFileName;
  private int mySegmentEnd;
  private PsiFile myPsiFile;
  private int lineCount;
  private int myFirstLineNumber;
  private boolean myPrintLineNumbers;
  private int myColumn;
  private LineMarkerInfo[] myMethodSeparators;
  private int myCurrentMethodSeparator;
  private Project myProject;

  public HTMLTextPainter(PsiFile psiFile, Project project, String dirName, boolean printLineNumbers) {
    myProject = project;
    myPsiFile = psiFile;
    myPrintLineNumbers = printLineNumbers;
    myHighlighter = HighlighterFactory.createHighlighter(project, psiFile.getVirtualFile());

//    String fileType = FileTypeManager.getInstance().getType(psiFile.getVirtualFile().getName());
//    myForceFonts =
//      FileTypeManager.TYPE_HTML.equals(fileType) ||
//      FileTypeManager.TYPE_XML.equals(fileType) ||
//      FileTypeManager.TYPE_JSP.equals(fileType);

    myText = psiFile.getText();
    myHighlighter.setText(myText);
    mySegmentEnd = myText.length();
    myFileName = psiFile.getVirtualFile().getPresentableUrl();
    myHTMLFileName = dirName + File.separator + ExportToHTMLManager.getHTMLFileName(psiFile);

    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    psiDocumentManager.commitAllDocuments();
    Document document = psiDocumentManager.getDocument(psiFile);

    GeneralHighlightingPass action = new GeneralHighlightingPass(myProject, psiFile, document, 0, psiFile.getTextLength(), false, true);
    Collection<LineMarkerInfo> lineMarkerInfos = action.queryLineMarkers();
    ArrayList<LineMarkerInfo> methodSeparators = new ArrayList<LineMarkerInfo>();
    for (LineMarkerInfo lineMarkerInfo : lineMarkerInfos) {
      if (lineMarkerInfo.separatorColor != null) {
        methodSeparators.add(lineMarkerInfo);
      }
    }

    Collections.sort(methodSeparators, new Comparator<LineMarkerInfo>() {
      public int compare(LineMarkerInfo o1, LineMarkerInfo o2) {
        return o1.startOffset - o2.startOffset;
      }
    });

    myMethodSeparators = methodSeparators.toArray(new LineMarkerInfo[methodSeparators.size()]);
    myCurrentMethodSeparator = 0;
  }

  public void setSegment(int segmentStart, int segmentEnd, int firstLineNumber) {
    myOffset = segmentStart;
    mySegmentEnd = segmentEnd;
    myFirstLineNumber = firstLineNumber;
  }

  public void paint(TreeMap refMap, FileType fileType) {
    HighlighterIterator hIterator = myHighlighter.createIterator(myOffset);
    if(hIterator.atEnd()) return;
    FileWriter writer;
    try {
      writer = new FileWriter(myHTMLFileName);
    }
    catch(IOException e) {
      LOG.error(e.getMessage(), e);
      return;
    }
    lineCount = myFirstLineNumber;
    TextAttributes prevAttributes = null;
    Iterator refKeys = null;

    int refOffset = -1;
    PsiReference ref = null;
    if(refMap != null) {
      refKeys = refMap.keySet().iterator();
      if(refKeys.hasNext()) {
        Integer key = (Integer)refKeys.next();
        ref = (PsiReference)refMap.get(key);
        refOffset = key.intValue();
      }
    }

    int referenceEnd = -1;
    try {
      writeHeader(writer, myFileName);
      if (myFirstLineNumber == 0) {
        writeLineNumber(writer);
      }
      String closeTag = null;

      while (myCurrentMethodSeparator < myMethodSeparators.length) {
        LineMarkerInfo marker = myMethodSeparators[myCurrentMethodSeparator];
        if (marker != null && marker.startOffset >= hIterator.getStart()) break;
        myCurrentMethodSeparator++;
      }

      while(!hIterator.atEnd()) {
        TextAttributes textAttributes = hIterator.getTextAttributes();
        int hStart = hIterator.getStart();
        int hEnd = hIterator.getEnd();
        if (hEnd > mySegmentEnd) break;
        if(refOffset > 0 && hStart <= refOffset && hEnd > refOffset) {
          referenceEnd = writeReferenceTag(writer, ref);
        }
//        if(myForceFonts || !equals(prevAttributes, textAttributes)) {
        if(!equals(prevAttributes, textAttributes)) {
          if(closeTag != null) {
            writer.write(closeTag);
          }
          closeTag = writeFontTag(writer, textAttributes);
          prevAttributes = textAttributes;
        }

        if (myCurrentMethodSeparator < myMethodSeparators.length) {
          LineMarkerInfo marker = myMethodSeparators[myCurrentMethodSeparator];
          if (marker != null && marker.startOffset <= hEnd) {
            writer.write("<hr>");
            myCurrentMethodSeparator++;
          }
        }

        writeString(writer, myText, hStart, hEnd - hStart, fileType);
//        if(closeTag != null) {
//          writer.write(closeTag);
//        }
        if(referenceEnd > 0 && hEnd >= referenceEnd) {
          writer.write("</A>");
          referenceEnd = -1;
          if(refKeys.hasNext()) {
            Integer key = (Integer)refKeys.next();
            ref = (PsiReference)refMap.get(key);
            refOffset = key.intValue();
          }
        }
        hIterator.advance();
      }
      if(closeTag != null) {
        writer.write(closeTag);
      }
      writeFooter(writer);
    }
    catch(IOException e) {
      LOG.error(e.getMessage(), e);
    }
    finally {
      try {
        writer.close();
      }
      catch(IOException e) {
        LOG.error(e.getMessage(), e);
      }
    }
  }

  private int writeReferenceTag(Writer writer, PsiReference ref) throws IOException {
    PsiClass refClass = (PsiClass)ref.resolve();

    PsiFile refFile = refClass.getContainingFile();
    PsiPackage refPackage = refFile.getContainingDirectory().getPackage();
    String refPackageName = "";
    if(refPackage != null) {
      refPackageName = refPackage.getQualifiedName();
    }

    PsiPackage psiPackage = myPsiFile.getContainingDirectory().getPackage();
    String psiPackageName = "";
    if(psiPackage != null) {
      psiPackageName = psiPackage.getQualifiedName();
    }

    StringBuffer fileName = new StringBuffer();
    StringTokenizer tokens = new StringTokenizer(psiPackageName, ".");
    while(tokens.hasMoreTokens()) {
      tokens.nextToken();
      fileName.append("../");
    }

    StringTokenizer refTokens = new StringTokenizer(refPackageName, ".");
    while(refTokens.hasMoreTokens()) {
      String token = refTokens.nextToken();
      fileName.append(token);
      fileName.append('/');
    }
    fileName.append(ExportToHTMLManager.getHTMLFileName(refFile));
    writer.write("<A href=\""+fileName+"\">");
    return ref.getElement().getTextRange().getEndOffset();
  }

  private String writeFontTag(Writer writer, TextAttributes textAttributes) throws IOException {
//    "<FONT COLOR=\"#000000\">"
    StringBuffer openTag = new StringBuffer();
    StringBuffer closeTag = new StringBuffer();
    createFontTags(textAttributes, openTag, closeTag);
    writer.write(openTag.toString());
    return closeTag.toString();
  }

  private void createFontTags(TextAttributes textAttributes, StringBuffer openTag, StringBuffer closeTag) {
    if(textAttributes.getForegroundColor() != null) {
      openTag.append("<FONT style=\"font-family:monospaced;\" COLOR=");
      appendColor(openTag, textAttributes.getForegroundColor());
      openTag.append(">");
      closeTag.insert(0, "</FONT>");
    }
    if((textAttributes.getFontType() & Font.BOLD) != 0) {
      openTag.append("<B>");
      closeTag.insert(0, "</B>");
    }
    if((textAttributes.getFontType() & Font.ITALIC) != 0) {
      openTag.append("<I>");
      closeTag.insert(0, "</I>");
    }
  }

  private void writeString(Writer writer, CharSequence charArray, int start, int length, FileType fileType) throws IOException {
    for(int i=start; i<start+length; i++) {
      char c = charArray.charAt(i);
      if(c=='<') {
        writeChar(writer, "&lt;");
      }
      else if(c=='>') {
        writeChar(writer, "&gt;");
      }
      else if (c=='&') {
        writeChar(writer, "&amp;");
      }
      else if (c=='\"') {
        writeChar(writer, "&quot;");
      }
      else if (c == '\t') {
        int tabSize = CodeStyleSettingsManager.getSettings(myProject).getTabSize(fileType);
        if (tabSize <= 0) tabSize = 1;
        int nSpaces = tabSize - myColumn % tabSize;
        for (int j = 0; j < nSpaces; j++) {
          writeChar(writer, " ");
        }
      }
      else if (c == '\n' || c == '\r') {
        if (c == '\r' && i+1 < start+length && charArray.charAt(i+1) == '\n') {
          writeChar(writer, " \r");
          i++;
        }
        else if (c == '\n') {
          writeChar(writer, " ");
        }
        writeLineNumber(writer);
      }
      else {
        writeChar(writer, String.valueOf(c));
      }
    }
  }

  private void writeChar(Writer writer, String s) throws IOException {
    writer.write(s);
    myColumn++;
  }

  private void writeLineNumber(Writer writer) throws IOException {
    writer.write('\n');
    myColumn = 0;
    if (myPrintLineNumbers) {
      lineCount++;

      writer.write("<FONT COLOR=0 STYLE=\"font-style:normal\">");

//      String numberCloseTag = writeFontTag(writer, ourLineNumberAttributes);

      String s = Integer.toString(lineCount);
      writer.write(s);
      int extraSpaces = 4 - s.length();
      do {
        writer.write(' ');
      } while (extraSpaces-- > 0);

      writer.write("</FONT>");

//      if (numberCloseTag != null) {
//        writer.write(numberCloseTag);
//      }
    }
  }

  private void writeHeader(Writer writer, String title) throws IOException {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    writer.write("<HTML>\r\n");
    writer.write("<HEAD>\r\n");
    writer.write("<TITLE>" + title + "</TITLE>\r\n");
    writer.write("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=windows-1252\">\r\n");
    writer.write("<META NAME=\"KEYWORDS\" CONTENT=\"IntelliJ_IDEA_Html\">\r\n");
    writer.write("</HEAD>\r\n");
    Color color = scheme.getDefaultBackground();
    if (color==null) color = Color.gray;
    writer.write("<BODY BGCOLOR=\"#" + Integer.toString(color.getRGB() & 0xFFFFFF, 16) + "\">\r\n");
    writer.write("<TABLE CELLSPACING=0 CELLPADDING=5 COLS=1 WIDTH=\"100%\" BGCOLOR=\"#C0C0C0\" >\r\n");
    writer.write("<TR><TD><CENTER>\r\n");
    writer.write("<FONT FACE=\"Arial, Helvetica\" COLOR=\"#000000\">\r\n");
    writer.write(title + "</FONT>\r\n");
    writer.write("</center></TD></TR></TABLE>\r\n");
    writer.write("<PRE>\r\n");
  }

  private void writeFooter(Writer writer) throws IOException {
    writer.write("</PRE>\r\n");
    writer.write("</BODY>\r\n");
    writer.write("</HTML>");
  }

  private void appendColor(StringBuffer buffer, Color color) {
    int red1 = color.getRed()/16;
    int red2 = color.getRed()%16;
    int green1 = color.getGreen()/16;
    int green2 = color.getGreen()%16;
    int blue1 = color.getBlue()/16;
    int blue2 = color.getBlue()%16;
    buffer.append("\"#");
    buffer.append(Integer.toHexString(red1));
    buffer.append(Integer.toHexString(red2));
    buffer.append(Integer.toHexString(green1));
    buffer.append(Integer.toHexString(green2));
    buffer.append(Integer.toHexString(blue1));
    buffer.append(Integer.toHexString(blue2));
    buffer.append("\"");
  }

  private boolean equals(TextAttributes attributes1, TextAttributes attributes2) {
    if (attributes2 == null) {
      return attributes1 == null;
    }
    if(attributes1 == null) {
      return false;
    }
    if(!Comparing.equal(attributes1.getForegroundColor(), attributes2.getForegroundColor())) {
      return false;
    }
    if(attributes1.getFontType() != attributes2.getFontType()) {
      return false;
    }
    if(!Comparing.equal(attributes1.getBackgroundColor(), attributes2.getBackgroundColor())) {
      return false;
    }
    if(!Comparing.equal(attributes1.getEffectColor(), attributes2.getEffectColor())) {
      return false;
    }
    return true;
  }

  public String getHTMLFileName() {
    return myHTMLFileName;
  }
}