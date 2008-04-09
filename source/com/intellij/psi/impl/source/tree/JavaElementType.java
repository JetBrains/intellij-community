package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.parsing.JavaParsingContext;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.tree.IChameleonElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IErrorCounterChameleonElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CharTable;

public interface JavaElementType {
  IFileElementType JAVA_FILE = JavaStubElementTypes.FILE;

  IElementType CLASS = JavaStubElementTypes.CLASS;
  IElementType ANONYMOUS_CLASS = JavaStubElementTypes.ANONYMOUS_CLASS;
  IElementType ENUM_CONSTANT_INITIALIZER = JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER;

  IElementType TYPE_PARAMETER = JavaStubElementTypes.TYPE_PARAMETER;
  IElementType TYPE_PARAMETER_LIST = JavaStubElementTypes.TYPE_PARAMETER_LIST;

  IElementType IMPORT_STATEMENT = JavaStubElementTypes.IMPORT_STATEMENT;
  IElementType IMPORT_STATIC_STATEMENT = JavaStubElementTypes.IMPORT_STATIC_STATEMENT;

  IElementType MODIFIER_LIST = JavaStubElementTypes.MODIFIER_LIST;
  IElementType EXTENDS_LIST = JavaStubElementTypes.EXTENDS_LIST;
  IElementType IMPLEMENTS_LIST = JavaStubElementTypes.IMPLEMENTS_LIST;
  IElementType FIELD = JavaStubElementTypes.FIELD;
  IElementType ENUM_CONSTANT = JavaStubElementTypes.ENUM_CONSTANT;
  IElementType METHOD = JavaStubElementTypes.METHOD;

  IElementType CLASS_INITIALIZER = JavaStubElementTypes.CLASS_INITIALIZER;
  IElementType PARAMETER = JavaStubElementTypes.PARAMETER;
  IElementType PARAMETER_LIST = JavaStubElementTypes.PARAMETER_LIST;
  IElementType EXTENDS_BOUND_LIST = JavaStubElementTypes.EXTENDS_BOUND_LIST;
  IElementType THROWS_LIST = JavaStubElementTypes.THROWS_LIST;

  IElementType IMPORT_STATIC_REFERENCE = new IJavaElementType("IMPORT_STATIC_REFERENCE");
  IElementType TYPE = new IJavaElementType("TYPE");
  IElementType REFERENCE_PARAMETER_LIST = new IJavaElementType("REFERENCE_PARAMETER_LIST");
  IElementType JAVA_CODE_REFERENCE = new IJavaElementType("JAVA_CODE_REFERENCE");
  IElementType PACKAGE_STATEMENT = new IJavaElementType("PACKAGE_STATEMENT");

  IElementType LOCAL_VARIABLE = new IJavaElementType("LOCAL_VARIABLE");
  IElementType REFERENCE_EXPRESSION = new IJavaElementType("REFERENCE_EXPRESSION");
  IElementType LITERAL_EXPRESSION = new IJavaElementType("LITERAL_EXPRESSION");
  IElementType THIS_EXPRESSION = new IJavaElementType("THIS_EXPRESSION");
  IElementType SUPER_EXPRESSION = new IJavaElementType("SUPER_EXPRESSION");
  IElementType PARENTH_EXPRESSION = new IJavaElementType("PARENTH_EXPRESSION");
  IElementType METHOD_CALL_EXPRESSION = new IJavaElementType("METHOD_CALL_EXPRESSION");
  IElementType TYPE_CAST_EXPRESSION = new IJavaElementType("TYPE_CAST_EXPRESSION");
  IElementType PREFIX_EXPRESSION = new IJavaElementType("PREFIX_EXPRESSION");
  IElementType POSTFIX_EXPRESSION = new IJavaElementType("POSTFIX_EXPRESSION");
  IElementType BINARY_EXPRESSION = new IJavaElementType("BINARY_EXPRESSION");
  IElementType CONDITIONAL_EXPRESSION = new IJavaElementType("CONDITIONAL_EXPRESSION");
  IElementType ASSIGNMENT_EXPRESSION = new IJavaElementType("ASSIGNMENT_EXPRESSION");
  IElementType NEW_EXPRESSION = new IJavaElementType("NEW_EXPRESSION");
  IElementType ARRAY_ACCESS_EXPRESSION = new IJavaElementType("ARRAY_ACCESS_EXPRESSION");
  IElementType ARRAY_INITIALIZER_EXPRESSION = new IJavaElementType("ARRAY_INITIALIZER_EXPRESSION");
  IElementType INSTANCE_OF_EXPRESSION = new IJavaElementType("INSTANCE_OF_EXPRESSION");
  IElementType CLASS_OBJECT_ACCESS_EXPRESSION = new IJavaElementType("CLASS_OBJECT_ACCESS_EXPRESSION");
  IElementType EMPTY_EXPRESSION = new IJavaElementType("EMPTY_EXPRESSION");

  IElementType EXPRESSION_LIST = new IJavaElementType("EXPRESSION_LIST");

  IElementType EMPTY_STATEMENT = new IJavaElementType("EMPTY_STATEMENT");
  IElementType BLOCK_STATEMENT = new IJavaElementType("BLOCK_STATEMENT");
  IElementType EXPRESSION_LIST_STATEMENT = new IJavaElementType("EXPRESSION_LIST_STATEMENT");
  IElementType DECLARATION_STATEMENT = new IJavaElementType("DECLARATION_STATEMENT");
  IElementType IF_STATEMENT = new IJavaElementType("IF_STATEMENT");
  IElementType WHILE_STATEMENT = new IJavaElementType("WHILE_STATEMENT");
  IElementType FOR_STATEMENT = new IJavaElementType("FOR_STATEMENT");
  IElementType FOREACH_STATEMENT = new IJavaElementType("FOREACH_STATEMENT");
  IElementType DO_WHILE_STATEMENT = new IJavaElementType("DO_WHILE_STATEMENT");
  IElementType SWITCH_STATEMENT = new IJavaElementType("SWITCH_STATEMENT");
  IElementType SWITCH_LABEL_STATEMENT = new IJavaElementType("SWITCH_LABEL_STATEMENT");
  IElementType BREAK_STATEMENT = new IJavaElementType("BREAK_STATEMENT");
  IElementType CONTINUE_STATEMENT = new IJavaElementType("CONTINUE_STATEMENT");
  IElementType RETURN_STATEMENT = new IJavaElementType("RETURN_STATEMENT");
  IElementType THROW_STATEMENT = new IJavaElementType("THROW_STATEMENT");
  IElementType SYNCHRONIZED_STATEMENT = new IJavaElementType("SYNCHRONIZED_STATEMENT");
  IElementType TRY_STATEMENT = new IJavaElementType("TRY_STATEMENT");
  IElementType LABELED_STATEMENT = new IJavaElementType("LABELED_STATEMENT");
  IElementType ASSERT_STATEMENT = new IJavaElementType("ASSERT_STATEMENT");

  IElementType CATCH_SECTION = new IJavaElementType("CATCH_SECTION");

  IElementType ANNOTATION_METHOD = new IJavaElementType("ANNOTATION_METHOD");
  IElementType ANNOTATION_ARRAY_INITIALIZER = new IJavaElementType("ANNOTATION_ARRAY_INITIALIZER");
  IElementType ANNOTATION = new IJavaElementType("ANNOTATION");
  IElementType NAME_VALUE_PAIR = new IJavaElementType("NAME_VALUE_PAIR");
  IElementType ANNOTATION_PARAMETER_LIST = new IJavaElementType("ANNOTATION_PARAMETER_LIST");

  IElementType IMPORT_LIST = new IChameleonElementType("IMPORT_LIST_TEXT", StdLanguages.JAVA){
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence seq = ((LeafElement)chameleon).getInternedText();
      final PsiManager manager = chameleon.getTreeParent().getPsi().getManager();
      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(TreeUtil.getFileElement((LeafElement)chameleon).getPsi());
      final JavaParsingContext context = new JavaParsingContext(SharedImplUtil.findCharTableByTree(chameleon), languageLevel);
      return context.getImportsTextParsing().parseImportsText(manager, new JavaLexer(languageLevel),
                                                              seq, 0, seq.length(), 0);
    }
    public boolean isParsable(CharSequence buffer, final Project project) {return false;}
  };

  IElementType CODE_BLOCK = new IErrorCounterChameleonElementType("CODE_BLOCK", StdLanguages.JAVA){
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence seq = ((LeafElement)chameleon).getInternedText();
      final PsiManager manager = chameleon.getTreeParent().getPsi().getManager();
      final CharTable table = SharedImplUtil.findCharTableByTree(chameleon);
      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(TreeUtil.getFileElement((LeafElement)chameleon).getPsi());
      JavaParsingContext context = new JavaParsingContext(table, languageLevel);
      return context.getStatementParsing().parseCodeBlockText(manager, new JavaLexer(languageLevel),
                                                              seq, 0, seq.length(), 0).getFirstChildNode();
    }
    public int getErrorsCount(CharSequence seq, Project project) {
      final Lexer lexer = new JavaLexer(LanguageLevel.HIGHEST);

      lexer.start(seq, 0, seq.length(), 0);
      if(lexer.getTokenType() != JavaTokenType.LBRACE) return FATAL_ERROR;
      lexer.advance();
      int balance = 1;
      IElementType type;
      while(true){
        type = lexer.getTokenType();
        if (type == null) break;
        if(balance == 0) return FATAL_ERROR;
        if (type == JavaTokenType.LBRACE) {
          balance++;
        }
        else if (type == JavaTokenType.RBRACE) {
          balance--;
        }
        lexer.advance();
      }
      return balance;
    }
  };

  IElementType EXPRESSION_STATEMENT = new IChameleonElementType("EXPRESSION_STATEMENT", StdLanguages.JAVA){
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence chars = ((CharTableBasedLeafElementImpl)chameleon).getInternedText();
      final PsiManager manager = chameleon.getTreeParent().getPsi().getManager();
      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(TreeUtil.getFileElement((LeafElement)chameleon).getPsi());
      final JavaParsingContext context = new JavaParsingContext(SharedImplUtil.findCharTableByTree(chameleon), languageLevel);
      return context.getExpressionParsing().parseExpressionTextFragment(manager, chars, 0, chars.length(), 0);
    }
    public boolean isParsable(CharSequence buffer, final Project project) {return false;}
  };

  //The following are the children of code fragment
  IElementType STATEMENTS = new ICodeFragmentElementType("STATEMENTS", StdLanguages.JAVA){
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence chars = ((CharTableBasedLeafElementImpl)chameleon).getInternedText();
      final PsiManager manager = chameleon.getTreeParent().getPsi().getManager();
      final CharTable table = SharedImplUtil.findCharTableByTree(chameleon);
      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(TreeUtil.getFileElement((LeafElement)chameleon).getPsi());
      JavaParsingContext context = new JavaParsingContext(table, languageLevel);
      return context.getStatementParsing().parseStatements(manager, null, chars, 0, chars.length(), 0);
    }

    public boolean isParsable(CharSequence buffer, final Project project) {return false;}
  };

  IElementType EXPRESSION_TEXT = new ICodeFragmentElementType("EXPRESSION_TEXT", StdLanguages.JAVA){
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence chars = ((CharTableBasedLeafElementImpl)chameleon).getInternedText();
      final PsiManager manager = chameleon.getTreeParent().getPsi().getManager();
      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(TreeUtil.getFileElement((LeafElement)chameleon).getPsi());
      final JavaParsingContext context = new JavaParsingContext(SharedImplUtil.findCharTableByTree(chameleon), languageLevel);
      return context.getExpressionParsing().parseExpressionTextFragment(manager, chars, 0, chars.length(), 0);
    }
    public boolean isParsable(CharSequence buffer, final Project project) {return false;}
  };

  IElementType REFERENCE_TEXT = new ICodeFragmentElementType("REFERENCE_TEXT", StdLanguages.JAVA){
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence chars = ((CharTableBasedLeafElementImpl)chameleon).getInternedText();
      return Parsing.parseJavaCodeReferenceText(chameleon.getTreeParent().getPsi().getManager(), chars, 0, chars.length(), SharedImplUtil.findCharTableByTree(chameleon), true);
    }
    public boolean isParsable(CharSequence buffer, final Project project) {return false;}
  };

  IElementType TYPE_TEXT = new ICodeFragmentElementType("TYPE_TEXT", StdLanguages.JAVA){
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence chars = ((CharTableBasedLeafElementImpl)chameleon).getInternedText();
      return Parsing.parseTypeText(chameleon.getTreeParent().getPsi().getManager(), null, chars, 0, chars.length(), 0, SharedImplUtil.findCharTableByTree(chameleon));
    }
    public boolean isParsable(CharSequence buffer, final Project project) {return false;}
  };
}
