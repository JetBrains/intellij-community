/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.rearrangement;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.wrq.rearranger.settings.ForceBlankLineSetting;
import com.wrq.rearranger.settings.RearrangerSettings;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/** Responsible for adjusting the number of blank lines at strategic places in the source file. */
public class Spacer {

  private static final Logger LOG = Logger.getInstance("#" + Spacer.class.getName());

  private final PsiFile            myFile;
  private final Document           myDocument;
  private final RearrangerSettings mySettings;
  private final char[]             myNewlineChars;
  private       boolean            myChangesMade;
  private       StringBuilder      myBuffer;
  private Map<PsiWhiteSpace, VirtualElement> virtualElements = new HashMap<PsiWhiteSpace, VirtualElement>();

  public Spacer(PsiFile psiFile, Document document, RearrangerSettings settings) {
    myFile = psiFile;
    myDocument = document;
    mySettings = settings;
    myChangesMade = false;
    int size = 0;
    size += settings.getAfterClassLBrace().getnBlankLines() + 1;
    size += settings.getBeforeMethodLBrace().getnBlankLines() + 1;
    size += settings.getAfterMethodLBrace().getnBlankLines() + 1;
    size += settings.getBeforeMethodRBrace().getnBlankLines() + 1;
    size += settings.getAfterMethodRBrace().getnBlankLines() + 1;
    size += settings.getBeforeClassRBrace().getnBlankLines() + 1;
    size += settings.getAfterClassRBrace().getnBlankLines() + 1;
    LOG.debug("constructor allocating " + size + " newline chars for max insertion");
    myNewlineChars = new char[size];
    while (size > 0) {
      myNewlineChars[--size] = '\n';
    }
    LOG.debug(settings.getAfterClassLBrace().toString());
    LOG.debug(settings.getBeforeMethodLBrace().toString());
    LOG.debug(settings.getAfterMethodLBrace().toString());
    LOG.debug(settings.getBeforeMethodRBrace().toString());
    LOG.debug(settings.getAfterMethodRBrace().toString());
    LOG.debug(settings.getBeforeClassRBrace().toString());
    LOG.debug(settings.getAfterClassRBrace().toString());
    final int ROOM_FOR_EXPANSION = 100;
    int maxSize = document.getTextLength() + ROOM_FOR_EXPANSION;
    myBuffer = new StringBuilder(maxSize);
  }

  private static class AbortRespacingException extends RuntimeException {
  }

  private static class BadPsiElementException extends Exception {
    BadPsiElementException(String string) {
      super(string);
    }
  }

  private static void handleBadPsiElementException(String desc, PsiElement element)
    throws AbortRespacingException
  {
    JOptionPane.showMessageDialog(null,
                                  "Spacing could not be performed due to a syntax error:\n" +
                                  desc + element.getText(), "Spacing Error", JOptionPane.ERROR_MESSAGE);
    throw new AbortRespacingException();
  }

  public boolean respace() {
    myBuffer.append(myDocument.getText());

    JavaElementVisitor visitor = new JavaRecursiveElementVisitor() {
      private int bias = 0;

      public void visitReferenceExpression(PsiReferenceExpression referenceExpression) {
      }

      public void visitFile(PsiFile psiFile) {
        super.visitFile(psiFile);
        if (mySettings.getNewLinesAtEOF().isForce()) {
          // remove all newlines at end of file, then append as many as are declared in configuration.
          while (myBuffer.length() > 0 && myBuffer.charAt(myBuffer.length() - 1) == '\n') {
            myBuffer.setLength(myBuffer.length() - 1);
          }
          for (int count = 0; count < mySettings.getNewLinesAtEOF().getnBlankLines(); count++) {
            myBuffer.append('\n');
          }
        }
      }

      public void visitAnonymousClass(PsiAnonymousClass psiAnonymousClass) {
        super.visitAnonymousClass(psiAnonymousClass);
      }

      public void visitClass(PsiClass psiClass) {
        if (psiClass instanceof PsiTypeParameter) {
          return;
        }
        boolean anonymous = psiClass.getName() == null;
        int oldbias = bias;
        try {
          bias += adjustSpacing(
            psiClass.getLBrace(),
            psiClass.getRBrace(),
            mySettings.getAfterClassLBrace(),
            bias
          );
        }
        catch (BadPsiElementException badPsiElement) {
          handleBadPsiElementException("class " + psiClass.getName() + " missing left brace.  Body follows:", psiClass);
        }
        log(mySettings.getAfterClassLBrace(), psiClass.getName(), oldbias, bias);
        super.visitClass(psiClass);
        oldbias = bias;
        try {
          bias += adjustSpacing(
            psiClass.getRBrace(),
            psiClass.getLBrace(),
            mySettings.getBeforeClassRBrace(),
            bias
          );
        }
        catch (BadPsiElementException badPsiElement) {
          handleBadPsiElementException("class " + psiClass.getName() + " missing right brace.  Body follows:", psiClass);
        }
        log(mySettings.getBeforeClassRBrace(), psiClass.getName(), oldbias, bias);

        if (anonymous) {
          return;
        }

        /**
         * if this is the last class in a file, don't adjust spacing after right brace.
         * The setting for number of newline characters at end of file overrides it.
         */
        boolean lastClass = false;
        if (psiClass.getParent() instanceof PsiJavaFile) {
          lastClass = isLastMeaningfulElement(psiClass.getParent(), psiClass);
        }
        if (!lastClass) {
          oldbias = bias;
          try {
            bias += adjustSpacing(
              psiClass.getRBrace(),
              psiClass.getLBrace(),
              mySettings.getAfterClassRBrace(),
              bias
            );
          }
          catch (BadPsiElementException badPsiElement) {
            handleBadPsiElementException("class " + psiClass.getName() + " missing left brace.  Body follows:", psiClass);
          }
          log(mySettings.getAfterClassRBrace(), psiClass.getName(), oldbias, bias);
        }
        else {
          LOG.debug(
            "class " +
            psiClass.getName() +
            " is last in file, no 'after right brace' adjustment"
          );
        }
      }

      public void visitMethod(PsiMethod psiMethod) {
        int oldbias;
        /**
         * if method has no body, make no spacing adjustments - there are no braces. It must be an interface
         * or abstract method declaration.
         */
        if (psiMethod.getBody() == null) {
          LOG.debug("skipping interface or abstract method " + psiMethod.getName());
          super.visitMethod(psiMethod);
          return;
        }
        /**
         * if method has an empty body, do only the spacing before the opening brace and after the closing
         * brace.
         */
        boolean methodIsEmpty = true;
        {
          PsiElement element = psiMethod.getBody().getLBrace().getNextSibling();
          while (element != null) {
            if (!(element instanceof PsiWhiteSpace) &&
                element != psiMethod.getBody().getRBrace())
            {
              methodIsEmpty = false;
              break;
            }
            element = element.getNextSibling();
          }
        }
        if (!methodIsEmpty) {
          oldbias = bias;
          try {
            bias += adjustSpacing(
              psiMethod.getBody().getLBrace(),
              psiMethod.getBody().getRBrace(),
              mySettings.getBeforeMethodLBrace(),
              bias
            );
            bias += adjustSpacing(
              psiMethod.getBody().getLBrace(),
              psiMethod.getBody().getRBrace(),
              mySettings.getAfterMethodLBrace(),
              bias
            );
          }
          catch (BadPsiElementException badPsiElement) {
            handleBadPsiElementException("body of method " + psiMethod.getName() + " missing left brace.  Body follows:", psiMethod);
          }
          log(mySettings.getAfterMethodLBrace(), psiMethod.getName(), oldbias, bias);
          super.visitMethod(psiMethod);
          oldbias = bias;
          try {
            bias += adjustSpacing(
              psiMethod.getBody().getRBrace(),
              psiMethod.getBody().getLBrace(),
              mySettings.getBeforeMethodRBrace(),
              bias
            );
          }
          catch (BadPsiElementException badPsiElement) {
            handleBadPsiElementException("body of method " + psiMethod.getName() + " missing right brace.  Body follows:", psiMethod);
          }
          log(mySettings.getBeforeMethodRBrace(), psiMethod.getName(), oldbias, bias);
        }
        else {
          LOG.debug("method " + psiMethod.getName() + " is empty, no internal spacing changes");
        }
        /**
         * if this is the last method in a class, don't adjust spacing after right brace.
         * The class setting for number of spaces before closing right brace overrides it.
         * Normally this would make no difference, as the 2nd would cancel out the first;
         * but in the case where the method ends with "}});" (anonymous inner class as
         * parameter), an inserted blank line actually causes two newline characters to be
         * inserted; but the class rule only removes one of those.
         */
        boolean lastMethod = false;
        PsiClass owner = psiMethod.getContainingClass();
        if (owner == psiMethod.getParent()) {
          lastMethod = isLastMeaningfulElement(owner, psiMethod);
        }
        if (!lastMethod) {
          oldbias = bias;
          try {
            bias += adjustSpacing(
              psiMethod.getBody().getRBrace(),
              psiMethod.getBody().getLBrace(),
              mySettings.getAfterMethodRBrace(),
              bias
            );
          }
          catch (BadPsiElementException badPsiElement) {
            handleBadPsiElementException("body of method " + psiMethod.getName() + " missing right brace.  Body follows:", psiMethod);
          }
          log(mySettings.getAfterMethodRBrace(), psiMethod.getName(), oldbias, bias);
        }
        else {
          LOG.debug(
            "method " +
            psiMethod.getName() +
            " is last in class, no 'after right brace' adjustment"
          );
        }
      }

      public void visitCodeBlock(PsiCodeBlock psiCodeBlock) {
        int oldbias;
        if (!(psiCodeBlock.getParent() instanceof PsiMethod) &&
            mySettings.isRemoveBlanksInsideCodeBlocks())
        {
          oldbias = bias;
          try {
            bias += adjustSpacing(psiCodeBlock.getLBrace(),
                                  psiCodeBlock.getRBrace(), false, 0, bias);
          }
          catch (BadPsiElementException badPsiElement) {
            handleBadPsiElementException("code block missing left brace.  Content follows:\n", psiCodeBlock);
          }
          log("code block left brace", oldbias, bias);
        }
        super.visitCodeBlock(psiCodeBlock);
        if (!(psiCodeBlock.getParent() instanceof PsiMethod) &&
            mySettings.isRemoveBlanksInsideCodeBlocks())
        {
          oldbias = bias;
          try {
            bias += adjustSpacing(psiCodeBlock.getRBrace(), psiCodeBlock.getLBrace(), true, 0, bias);
          }
          catch (BadPsiElementException badPsiElement) {
            handleBadPsiElementException("code block missing right brace.  Content follows:\n", psiCodeBlock);
          }
          log("code block right brace", oldbias, bias);
        }
      }
    };
    try {
      myFile.accept(visitor);
    }
    catch (AbortRespacingException ar) {
      return false;
    }
    if (myChangesMade) {
      LOG.debug(
        "changes made to document; old length=" +
        myDocument.getTextLength() + ", new=" + myBuffer.length()
      );
      LOG.debug("old document is:\n" + myDocument.getText());
      LOG.debug("new document is:\n" + myBuffer.toString());
      myDocument.replaceString(0, myDocument.getTextLength(), myBuffer.toString());
    }
    return myChangesMade;
  }

  /**
   * Determines if the psiElement (method or class) is last syntactic item in the owner class.
   * If so, we don't want to change spacing after right brace of psiElement; this spacing is
   * overridden by the parent class's spacing before right brace.
   *
   * @param owner      class element to which psiElement belongs.
   * @param psiElement method or inner class to be tested.
   * @return true if the psiElement is the last syntactic item (i.e., not including comments and whitespace)
   *         of the owner.
   */
  private static boolean isLastMeaningfulElement(PsiElement owner, PsiElement psiElement) {
    boolean lastMethod;
    // class is immediate parent
    PsiElement[] elements = owner.getChildren();
    boolean sawThisMethod = false;
    boolean sawSomethingElseLater = false;
    for (PsiElement element : elements) {
      if (!sawThisMethod && element == psiElement) {
        sawThisMethod = true;
      }
      else if (sawThisMethod &&
               !(element instanceof PsiWhiteSpace) &&
               !(element instanceof PsiJavaToken &&
                 element.getText().equals("}")))
//                    ((PsiJavaToken) elements[i]).getTokenType() == PsiJavaToken.RBRACE))
      {
        sawSomethingElseLater = true;
        break;
      }
    }
    lastMethod = sawThisMethod && !sawSomethingElseLater;
    return lastMethod;
  }

  private static void log(ForceBlankLineSetting fbls, String name, int oldbias, int bias) {
    if (oldbias != bias) {
      LOG.debug(
        fbls.getObjectName() +
        " " +
        name +
        (fbls.isBefore() ? " before" : " after") +
        (fbls.isOpenBrace() ? " left brace:" : " right brace:") +
        (oldbias < bias ? " inserted " + (bias - oldbias)
                        : " removed " + (oldbias - bias)) + " newlines"
      );
    }
  }

  private static void log(String name, int oldbias, int bias) {
    if (oldbias != bias) {
      LOG.debug(
        name +
        (oldbias < bias ? " inserted " + (bias - oldbias)
                        : " removed " + (oldbias - bias)) + " newlines"
      );
    }
  }

  private int adjustSpacing(PsiElement brace, PsiElement matchingBrace, ForceBlankLineSetting fbls, int bias)
    throws BadPsiElementException
  {
    if (fbls.isForce()) {
      return adjustSpacing(brace, matchingBrace, fbls.isBefore(), fbls.getnBlankLines(), bias);
    }
    else {
      return 0;
    }
  }

  /**
   * When whiteSpace is changed based on contents of a PsiWhiteSpace element, we need to keep track of that
   * so that if a subsequent adjustment in spacing is based on that same element, we use the new whitespace value.
   */
  private class VirtualElement {
    private       String textValue;
    private final int    textOffset;

    VirtualElement(PsiWhiteSpace whiteSpace, int bias) {
      textValue = whiteSpace.getText();
      textOffset = whiteSpace.getTextOffset() + bias;
      virtualElements.put(whiteSpace, this);
    }

    String getText() {
      return textValue;
    }

    public void setTextValue(String textValue) {
      this.textValue = textValue;
    }

    public int getTextOffset() {
      return textOffset;
    }
  }

  /**
   * Determine the number of blank lines actually occurring before or after the brace.  If this is not
   * the desired number, alter the whitespace before or after the brace to contain the desired number
   * of blank lines.
   *
   * @param brace
   * @param before
   * @param nBlankLines
   * @param bias
   * @return bias adjustment corresponding to number of newlines inserted or deleted
   */
  private int adjustSpacing(PsiElement brace, PsiElement matchingBrace, boolean before, int nBlankLines, int bias)
    throws BadPsiElementException
  {
    int result = 0;
    if (brace == null) {
      throw new BadPsiElementException(
        "adjustSpacing: illegal syntax (mismatched braces); PsiElement for brace is null"
      );
    }
    /*
    * ensure that character at offset indicated by brace element is in fact a brace.
    */
    int offset = brace.getTextRange().getStartOffset() + bias;
    int direction = before ? -1 : +1;
    int count = 0;
    {
      char braceChar = myBuffer.charAt(offset);
      if (braceChar != '{' && braceChar != '}') {
        int L = offset - 50;
        int R = offset + 50;
        if (L < 0) {
          L = 0;
        }
        if (R > myBuffer.length()) {
          R = myBuffer.length();
        }
        String context = myBuffer.toString().substring(L, R);
        throw new RuntimeException(
          "adjustSpacing: char at offset " +
          offset +
          " is not a brace, ='" +
          braceChar + "'" + "; context is " + context
        );
      }
    }
    /*
    * Locate the whitespace that precedes or follows this brace and which contains newline characters.
    * Intervening text, such as comments and other syntactic items, could be skipped.
    */
    PsiElement whiteSpace = brace;
    do {
      whiteSpace = getNextElement(whiteSpace, before);
      if (whiteSpace == matchingBrace) {
        /**
         * Don't parse past the matching brace.  This could occur if a class or method body is empty and
         * the left and right braces are on the same line with no intervening whitespace.
         */
        return result;
      }
    }
    while (whiteSpace != null && (!(whiteSpace instanceof PsiWhiteSpace) || whiteSpace.getText().indexOf('\n') < 0));

    VirtualElement virtualWhiteSpace = null;
    if (whiteSpace != null) {
      virtualWhiteSpace = (virtualElements.get(whiteSpace));
      if (virtualWhiteSpace == null) {
        virtualWhiteSpace = new VirtualElement((PsiWhiteSpace)whiteSpace, bias);
      }
      else {
        LOG.debug("reusing virtualWhiteSpace, offset=" + virtualWhiteSpace.textOffset);
      }
      /*
      * now count the number of newlines.
      */
      String s = virtualWhiteSpace.getText();
      for (int i = 0; i < s.length(); i++) {
        if (s.charAt(i) == '\n') {
          count++;
        }
      }
    }

    /**
     * first count the number of existing blank lines.
     */
    if (offset + direction >= myBuffer.length()) {
      LOG.debug("at EOF, don't append any extra blank lines");
      // we're at end of file.  Don't append any extra blank lines.
      nBlankLines = 0;
    }
    /**
     * Count of one means there was a single newline character next to the brace.
     * This can be counted as the one that belongs on the brace's line.  So the real number
     * of consecutive newline characters desired is nBlankLines + 1.
     */
    int desiredNewlineChars = nBlankLines + 1;
    if (desiredNewlineChars == 1 && count == 0 && before) {
      // no blank lines to precede this text; in fact, there's prior text and no newline character at all.
      // this is acceptable.
      desiredNewlineChars = 0;
    }
    {
      PsiElement element = brace.getParent();
      if (element instanceof PsiCodeBlock &&
          element.getParent() instanceof PsiMethod)
      {
        element = element.getParent();
      }
      LOG.debug(
        "adjustSpacing: " +
        (before ? "before " : "after ") +
        element.toString() +
        " " +
        brace.getText() +
        ", desire " + desiredNewlineChars + " newlines, have " + count
      );
    }
    if (desiredNewlineChars == count) {
      return 0;
    }
    /*
    * determine end index of whitespace to be replaced.
    * keep trailing spaces/tabs.
    */
    int endIndex;
//        if (whiteSpace != null) // todo - try modifying the PSI tree
//        {
//            try
//            {
//                StringBuilder sbr = new StringBuilder(whiteSpace.getText());
//                if (desiredNewlineChars > count) sbr.append(newlineChars, 0, desiredNewlineChars - count);
//                else sbr.setLength(sbr.length() + desiredNewlineChars - count);
//                PsiElement codeblk = factory.createCodeBlockFromText(sbr.toString(), element);
//                whiteSpace.replace(codeblk.getFirstChild());
//            }
//            catch (IncorrectOperationException e)
//            {
//                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
//            }
//        }
    if (virtualWhiteSpace != null) {
      String s = virtualWhiteSpace.getText();
      for (endIndex = s.length() - 1; endIndex >= 0; endIndex--) {
        if (s.charAt(endIndex) != ' ' &&
            s.charAt(endIndex) != '\t')
        {
          break;
        }
      } // now endIndex points to the last newline character in the whitespace.
      offset = virtualWhiteSpace.getTextOffset();
      endIndex += offset + 1;
    }
    else {
      offset = endIndex = myBuffer.length();
    }
    try {
      myChangesMade = true;
      LOG.debug(
        "sb.replace(" +
        offset +
        "," +
        endIndex + ") with " + desiredNewlineChars + " newline characters"
      );
      myBuffer.replace(offset, endIndex, new String(myNewlineChars, 0, desiredNewlineChars));
      result = desiredNewlineChars - (endIndex - offset);
      // now update virtualWhiteSpace accordingly.
      if (virtualWhiteSpace != null) {
        offset -= virtualWhiteSpace.getTextOffset();
        endIndex -= virtualWhiteSpace.getTextOffset();
        StringBuffer vsb = new StringBuffer(virtualWhiteSpace.getText());
        vsb.replace(offset, endIndex, new String(myNewlineChars, 0, desiredNewlineChars));
        virtualWhiteSpace.setTextValue(vsb.toString());
      }
    }
    catch (StringIndexOutOfBoundsException si) {
      throw new RuntimeException(
        "sb.length()=" +
        myBuffer.length() +
        ", offset=" +
        offset +
        ", before=" +
        before +
        ", count=" +
        count +
        ", desiredNewlineChars=" +
        desiredNewlineChars +
        ", charAt offset=" + (offset < myBuffer.length() ? "" + myBuffer.charAt(offset) : "OOB"), si
      );
    }
    return result;
  }

  /**
   * Returns the leaf psiElement before or after the given element.
   *
   * @param element original element.
   * @param before  if true, return prior leaf element; else return next left element.
   * @return adjacent leaf element.
   */
  private PsiElement getNextElement(PsiElement element, boolean before) {
    PsiElement result;
    while (true) {
      result = before ? element.getPrevSibling() : element.getNextSibling();
      if (result == null) {
        // first/last sibling; go to parent's previous/next item.
        result = element.getParent();
        if (result instanceof PsiFile) {
          // don't go beyond this document!
          result = null;
        }
        // if we started at the first/last leaf node, we run out of parents; there is no previous/next leaf element.
        if (result == null) {
          break;
        }
        else {
          element = result;
        }
      }
      else {
        break;
      }
    }
    while (result != null && result.getChildren().length != 0) {
      // previous/next sibling is parent of others; find last/first leaf node.
      result = result.getChildren()[before ? result.getChildren().length - 1 : 0];
    }
    return result;
  }
}
