package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.JavaTokenType;
import gnu.trove.THashSet;

/**
 * @author Maxim.Mossienko
*/
abstract class OptimizingSearchHelperBase implements OptimizingSearchHelper {
  private final THashSet<String> scanned;
  private final THashSet<String> scannedComments;
  private final THashSet<String> scannedLiterals;
  protected int scanRequest;
  private Lexer javaLexer;

  protected final CompileContext context;

  OptimizingSearchHelperBase(CompileContext _context) {
    context = _context;

    scanRequest = 0;
    scanned = new THashSet<String>();
    scannedComments = new THashSet<String>();
    scannedLiterals = new THashSet<String>();
  }

  public void clear() {
    scanned.clear();
    scannedComments.clear();
    scannedLiterals.clear();
  }

  public boolean addWordToSearchInCode(final String refname) {
    if (!scanned.contains(refname)) {
      boolean isJavaReservedWord = false;

      if (context.options.getFileType() == StdFileTypes.JAVA) {
        if (javaLexer == null) {
          javaLexer = LanguageParserDefinitions.INSTANCE.forLanguage(
            StdFileTypes.JAVA.getLanguage()
          ).createLexer(context.project);
        }
        javaLexer.start(refname);
        isJavaReservedWord = JavaTokenType.KEYWORD_BIT_SET.contains(javaLexer.getTokenType());
      }

      if (isJavaReservedWord) {
        doAddSearchJavaReservedWordInCode(refname);
      } else {
        doAddSearchWordInCode(refname);
      }

      scanned.add( refname );
      return true;
    }

    return false;
  }

  protected abstract void doAddSearchJavaReservedWordInCode(final String refname);
  protected abstract void doAddSearchWordInCode(final String refname);
  protected abstract void doAddSearchWordInComments(final String refname);
  protected abstract void doAddSearchWordInLiterals(final String refname);

  public void endTransaction() {
    scanRequest++;
  }

  public boolean addWordToSearchInComments(final String refname) {
    if (!scannedComments.contains(refname)) {
      doAddSearchWordInComments(refname);

      scannedComments.add( refname );
      return true;
    }
    return false;
  }

  public boolean addWordToSearchInLiterals(final String refname) {
    if (!scannedLiterals.contains(refname)) {
      doAddSearchWordInLiterals(refname);
      scannedLiterals.add( refname );
      return true;
    }
    return false;
  }

  public boolean isScannedSomething() {
    return scanned.size() > 0 || scannedComments.size() > 0 || scannedLiterals.size() > 0;
  }

}
