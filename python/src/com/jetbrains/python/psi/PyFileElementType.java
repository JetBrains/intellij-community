package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.tree.IStubFileElementType;
import com.jetbrains.python.lexer.PythonIndentingLexer;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.parsing.StatementParsing;

/**
* @author yole
*/
public class PyFileElementType extends IStubFileElementType {
  public PyFileElementType(Language language) {
    super(language);
  }

  @Override
  public int getStubVersion() {
    return 22;
  }

  @Override
  public ASTNode parseContents(ASTNode chameleon) {
    final FileElement node = (FileElement)chameleon;
    final LanguageLevel languageLevel = getLanguageLevel(node.getPsi());
    final Lexer lexer = new PythonIndentingLexer();

    final Project project = chameleon.getPsi().getProject();
    final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();

    final PsiBuilder builder = factory.createBuilder(project, chameleon, lexer, getLanguage(), chameleon.getChars());

    final PyParser parser = new PyParser(languageLevel);
    if (languageLevel == LanguageLevel.PYTHON26 &&
        node.getPsi().getContainingFile().getName().equals("__builtin__.py")) {
      parser.setFutureFlag(StatementParsing.FUTURE.PRINT_FUNCTION);      
    }

    return parser.parse(this, builder).getFirstChildNode();
  }

  private static LanguageLevel getLanguageLevel(PsiElement psi) {
    final PsiFile file = psi.getContainingFile();
    if (!(file instanceof PyFile)) {
      final PsiElement context = file.getContext();
      if (context != null) return getLanguageLevel(context);
      return LanguageLevel.getDefault();
    }
    return ((PyFile)file).getLanguageLevel();
  }
}
