package com.intellij.psi.formatter;

import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.FormattingModel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomModel;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.impl.PomTransactionBase;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.jsp.JspxFileImpl;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;

public class PsiBasedFormattingModel implements FormattingModel {
  private final Document myDocument;
  private final ASTNode myASTNode;
  private final Project myProject;
  private final CodeStyleSettings mySettings;

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.PsiBasedFormattingModel");
  private final boolean myCanUseDocument;

  public PsiBasedFormattingModel(final PsiFile file, CodeStyleSettings settings, final TextRange textRange) {
    mySettings = settings;
    myASTNode = SourceTreeToPsiMap.psiElementToTree(file);
    myProject = file.getProject();
    final Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
    myCanUseDocument = false;
    if (document != null && document.getText().equals(file.getText())) {      
      myDocument = document;
    }
    else {
      myDocument = new DocumentImpl(file.getText());
    }
  }

  public int getLineNumber(int offset) {
    if (offset > myDocument.getTextLength()) {
      LOG.assertTrue(false);
    }
    return myDocument.getLineNumber(offset);
  }

  public int getLineStartOffset(int line) {
    return myDocument.getLineStartOffset(line);
  }

  public void runModificationTransaction(final Runnable action) throws IncorrectOperationException {
    final PomModel model = myProject.getModel();
    final TreeAspect aspect = model.getModelAspect(TreeAspect.class);
    try {
      model.runTransaction(new PomTransactionBase(SourceTreeToPsiMap.treeElementToPsi(myASTNode)) {
        public PomModelEvent runInner() {
          final FileElement fileElement = getFileElement(myASTNode);
          action.run();
          TreeUtil.clearCaches(fileElement);
          return null;
        }

        private FileElement getFileElement(final ASTNode element) {
          return (FileElement)SourceTreeToPsiMap.psiElementToTree(SourceTreeToPsiMap.treeElementToPsi(element).getContainingFile());
        }
      }, aspect);
    }
    catch (IncorrectOperationException e) {
      throw e;
    }
  }

  public int getTextLength() {
    return myDocument.getTextLength();
  }

  public void commitChanges() {
    if (myCanUseDocument) {
      PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
    }    
  }

  public TextRange replaceWhiteSpace(TextRange textRange,
                                     String whiteSpace,
                                     final TextRange oldBlockTextRange,
                                     final boolean blockIsWritable) throws IncorrectOperationException {
    return replaceWithPSI(textRange, oldBlockTextRange, whiteSpace);
  }

  public Project getProject() {
    return myProject;
  }

  private TextRange replaceWithDocument(final TextRange textRange, final TextRange oldBlockTextRange, final String whiteSpace) {
    //final RangeMarker rangeMarker = myDocument.createRangeMarker(oldBlockTextRange.getStartOffset(), oldBlockTextRange.getEndOffset());
    
    myDocument.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), whiteSpace);
    
    return oldBlockTextRange;//new TextRange(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());

  }

  private TextRange replaceWithPSI(final TextRange textRange, final TextRange oldBlockTextRange, final String whiteSpace)
                                                                                                                          throws IncorrectOperationException {
//final RangeMarker rangeMarker = myDocument.createRangeMarker(oldBlockTextRange.getStartOffset(), oldBlockTextRange.getEndOffset());
    final int offset = textRange.getEndOffset();
    final ASTNode leafElement = findElementAt(offset);
    int length = oldBlockTextRange.getLength();
    final int oldElementLength = leafElement.getTextRange().getLength();
    if (leafElement.getTextRange().getStartOffset() < textRange.getStartOffset()) {
      final int newElementLength = new Helper(StdFileTypes.JAVA, myProject).shiftIndentInside(leafElement, getSpaceCount(whiteSpace))
        .getTextRange().getLength();
      length = length - oldElementLength + newElementLength;
    }
    else {
      FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, ElementType.WHITE_SPACE);
      if (leafElement.textContains('\n')
        /*&& whiteSpace.indexOf('\n') >= 0*/) {
        try {
          Indent lastLineIndent = getLastLineIndent(leafElement.getText());
          Indent whiteSpaceIndent = createIndentOn(getLastLine(whiteSpace));
          final int shift = calcShift(lastLineIndent, whiteSpaceIndent);
          final int newElementLength = new Helper(StdFileTypes.JAVA, myProject).shiftIndentInside(leafElement, shift).getTextRange()
            .getLength();
          length = length - oldElementLength + newElementLength;
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    return new TextRange(oldBlockTextRange.getStartOffset(), oldBlockTextRange.getStartOffset() + length);
  }

  private int calcShift(final Indent lastLineIndent, final Indent whiteSpaceIndent) {
    final CodeStyleSettings.IndentOptions options = mySettings.JAVA_INDENT_OPTIONS;
    if (lastLineIndent.equals(whiteSpaceIndent)) return 0;
    if (options.USE_TAB_CHARACTER) {
      if (lastLineIndent.whiteSpaces > 0) {
        return whiteSpaceIndent.getSpacesCount(options);
      }
      else {
        return whiteSpaceIndent.tabs - lastLineIndent.tabs;
      }
    }
    else {
      if (lastLineIndent.tabs > 0) {
        return whiteSpaceIndent.getTabsCount(options);
      }
      else {
        return whiteSpaceIndent.whiteSpaces - lastLineIndent.whiteSpaces;
      }
    }
  }

  private int getSpaceCount(final String whiteSpace) throws IncorrectOperationException {
    try {
      final String lastLine = getLastLine(whiteSpace);
      if (lastLine != null) {
        return lastLine.length();
      }
      else {
        return 0;
      }
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.getLocalizedMessage());
    }
  }

  private ASTNode findElementAt(final int offset) {
    if (myASTNode.getElementType() == ElementType.JSPX_FILE) {
      final PsiFile[] psiRoots = ((JspxFileImpl)SourceTreeToPsiMap.treeElementToPsi(myASTNode)).getPsiRoots();
      for (int i = 0; i < psiRoots.length; i++) {
        PsiFile psiRoot = psiRoots[i];
        final PsiElement found = psiRoot.findElementAt(offset);
        if (found != null && found.getTextRange().getStartOffset() == offset) return SourceTreeToPsiMap.psiElementToTree(found);
      }
    }
    return myASTNode.findLeafElementAt(offset);
  }

  public void dispose() {
  }

  private class Indent {
    public int whiteSpaces = 0;
    public int tabs = 0;

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final Indent indent = (Indent)o;

      if (tabs != indent.tabs) return false;
      if (whiteSpaces != indent.whiteSpaces) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = whiteSpaces;
      result = 29 * result + tabs;
      return result;
    }

    public int getTabsCount(final CodeStyleSettings.IndentOptions options) {
      final int tabsFromSpaces = whiteSpaces / options.TAB_SIZE;
      return tabs + tabsFromSpaces;
    }

    public int getSpacesCount(final CodeStyleSettings.IndentOptions options) {
      return whiteSpaces + tabs * options.TAB_SIZE;
    }
  }

  private Indent getLastLineIndent(final String text) throws IOException {
    String lastLine = getLastLine(text);
    if (lastLine == null) return new Indent();
    return createIndentOn(lastLine);
  }

  private Indent createIndentOn(final String lastLine) {
    final Indent result = new Indent();
    for (int i = 0; i < lastLine.length(); i++) {
      if (lastLine.charAt(i) == ' ') result.whiteSpaces += 1;
      if (lastLine.charAt(i) == '\t') result.tabs += 1;
    }
    return result;
  }

  private String getLastLine(final String text) throws IOException {
    final LineNumberReader lineNumberReader = new LineNumberReader(new StringReader(text));
    String line;
    String result = null;
    while ((line = lineNumberReader.readLine()) != null) {
      result = line;
    }
    return result;
  }

  public CharSequence getText(final TextRange textRange) {
    return myDocument.getCharsSequence().subSequence(textRange.getStartOffset(), textRange.getEndOffset());
  }
}
