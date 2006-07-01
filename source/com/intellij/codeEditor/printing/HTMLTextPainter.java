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
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.io.*;
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
  private Map<TextAttributes, String> myStyleMap = new HashMap<TextAttributes, String>();

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

    ArrayList<LineMarkerInfo> methodSeparators = new ArrayList<LineMarkerInfo>();
    if (document != null) {
      GeneralHighlightingPass action = new GeneralHighlightingPass(myProject, psiFile, document, 0, psiFile.getTextLength(), false, true);
      Collection<LineMarkerInfo> lineMarkerInfos = action.queryLineMarkers();
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
    }

    myMethodSeparators = methodSeparators.toArray(new LineMarkerInfo[methodSeparators.size()]);
    myCurrentMethodSeparator = 0;
  }

  public void setSegment(int segmentStart, int segmentEnd, int firstLineNumber) {
    myOffset = segmentStart;
    mySegmentEnd = segmentEnd;
    myFirstLineNumber = firstLineNumber;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void paint(TreeMap refMap, FileType fileType) throws FileNotFoundException {
    HighlighterIterator hIterator = myHighlighter.createIterator(myOffset);
    if(hIterator.atEnd()) return;
    OutputStreamWriter writer;
    try {
      writer = new OutputStreamWriter(new FileOutputStream(myHTMLFileName), "UTF-8");
    }
    catch (UnsupportedEncodingException e) {
      LOG.error(e);
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
      writeHeader(writer, new File(myFileName).getName());
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

        boolean haveNonWhiteSpace = false;
        for(int offset = hStart; offset < hEnd; offset++) {
          char c = myText.charAt(offset);
          if (c != ' ' && c != '\t') {
            haveNonWhiteSpace = true;
            break;
          }
        }
        if (!haveNonWhiteSpace) {
          // don't write separate spans for whitespace-only text fragments
          writeString(writer, myText, hStart, hEnd - hStart, fileType);
          hIterator.advance();
          continue;
        }

        if(refOffset > 0 && hStart <= refOffset && hEnd > refOffset) {
          referenceEnd = writeReferenceTag(writer, ref);
        }
//        if(myForceFonts || !equals(prevAttributes, textAttributes)) {
        if(!equals(prevAttributes, textAttributes) && referenceEnd < 0 ) {
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
          writer.write("</a>");
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
    if (!psiPackageName.equals(refPackageName)) {
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
    }
    fileName.append(ExportToHTMLManager.getHTMLFileName(refFile));
    //noinspection HardCodedStringLiteral
    writer.write("<a href=\""+fileName+"\">");
    return ref.getElement().getTextRange().getEndOffset();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private String writeFontTag(Writer writer, TextAttributes textAttributes) throws IOException {
//    "<FONT COLOR=\"#000000\">"
    writer.write("<span class=\"" + myStyleMap.get(textAttributes) + "\">");
    return "</span>";
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
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

  private void writeLineNumber(@NonNls Writer writer) throws IOException {
    writer.write('\n');
    myColumn = 0;
    lineCount++;
    writer.write("<a name=\"l" + lineCount + "\">");
    if (myPrintLineNumbers) {

//      String numberCloseTag = writeFontTag(writer, ourLineNumberAttributes);

      writer.write("<span class=\"ln\">");
      String s = Integer.toString(lineCount);
      writer.write(s);
      int extraSpaces = 4 - s.length();
      do {
        writer.write(' ');
      } while (extraSpaces-- > 0);
      writer.write("</span></a>");

//      if (numberCloseTag != null) {
//        writer.write(numberCloseTag);
//      }
    }
  }

  private void writeHeader(@NonNls Writer writer, String title) throws IOException {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    writer.write("<html>\r\n");
    writer.write("<head>\r\n");
    writer.write("<title>" + title + "</title>\r\n");
    writer.write("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\r\n");
    writeStyles(writer);
    writer.write("</head>\r\n");
    Color color = scheme.getDefaultBackground();
    if (color==null) color = Color.gray;
    writer.write("<BODY BGCOLOR=\"#" + Integer.toString(color.getRGB() & 0xFFFFFF, 16) + "\">\r\n");
    writer.write("<TABLE CELLSPACING=0 CELLPADDING=5 COLS=1 WIDTH=\"100%\" BGCOLOR=\"#C0C0C0\" >\r\n");
    writer.write("<TR><TD><CENTER>\r\n");
    writer.write("<FONT FACE=\"Arial, Helvetica\" COLOR=\"#000000\">\r\n");
    writer.write(title + "</FONT>\r\n");
    writer.write("</center></TD></TR></TABLE>\r\n");
    writer.write("<pre>\r\n");
  }

  private void writeStyles(@NonNls final Writer writer) throws IOException {
    writer.write("<style type=\"text/css\">\n");
    writer.write(".ln { color: rgb(0,0,0); font-weight: normal; font-style: normal; }\n");
    HighlighterIterator hIterator = myHighlighter.createIterator(myOffset);
    while(!hIterator.atEnd()) {
      TextAttributes textAttributes = hIterator.getTextAttributes();
      if (!myStyleMap.containsKey(textAttributes)) {
        @NonNls String styleName = "s" + myStyleMap.size();
        myStyleMap.put(textAttributes, styleName);
        writer.write("." + styleName + " { ");
        final Color foreColor = textAttributes.getForegroundColor();
        if (foreColor != null) {
          writer.write("color: rgb(" + foreColor.getRed() + "," + foreColor.getGreen() + "," + foreColor.getBlue() + "); ");
        }
        if ((textAttributes.getFontType() & Font.BOLD) != 0) {
          writer.write("font-weight: bold; ");
        }
        if ((textAttributes.getFontType() & Font.ITALIC) != 0) {
          writer.write("font-style: italic; ");
        }
        writer.write("}\n");
      }
      hIterator.advance();
    }
    writer.write("</style>\n");
  }

  private static void writeFooter(@NonNls Writer writer) throws IOException {
    writer.write("</pre>\r\n");
    writer.write("</body>\r\n");
    writer.write("</html>");
  }

  private static boolean equals(TextAttributes attributes1, TextAttributes attributes2) {
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