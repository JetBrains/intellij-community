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
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;

public class PsiBasedFormattingModel implements FormattingModel{
  private final Document myDocument;
  private final ASTNode myASTNode;
  private final Project myProject;
  private final CodeStyleSettings mySettings;

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.PsiBasedFormattingModel");

  public PsiBasedFormattingModel(final PsiFile file, CodeStyleSettings settings) {
    mySettings = settings;
    myASTNode = SourceTreeToPsiMap.psiElementToTree(file);
    myProject = file.getProject();
    final Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
    if (document != null && document.getText().equals(file.getText())) {
      myDocument = document;
    } else {
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
        public PomModelEvent runInner(){
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

  public void replaceWhiteSpace(final TextRange textRange, final String whiteSpace) throws IncorrectOperationException {
    final ASTNode leafElement = myASTNode.findLeafElementAt(textRange.getEndOffset());
    if (leafElement.getTextRange().getStartOffset() < textRange.getStartOffset()) {
      new Helper(StdFileTypes.JAVA, myProject).shiftIndentInside(leafElement, whiteSpace.length());
    } else {
      FormatterUtil.replaceWhiteSpace(whiteSpace,
                                                     leafElement,
                                                     ElementType.WHITE_SPACE);
      if (leafElement.textContains('\n') && whiteSpace.indexOf('\n') >= 0) {
        try {
          int lastLineIndent = getLastLineIndent(leafElement.getText());
          new Helper(StdFileTypes.JAVA, myProject).shiftIndentInside(leafElement, whiteSpace.length() - lastLineIndent);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

  }

  private int getLastLineIndent(final String text) throws IOException {
    final CodeStyleSettings.IndentOptions options = mySettings.JAVA_INDENT_OPTIONS;
    String lastLine = getLastLine(text);
    if (lastLine == null) return 0;
    int result = 0;
    for (int i = 0; i < lastLine.length(); i++) {
      if (lastLine.charAt(i) == ' ') result += 1;
      if (lastLine.charAt(i) == '\t') result += options.TAB_SIZE;
      return result;
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
