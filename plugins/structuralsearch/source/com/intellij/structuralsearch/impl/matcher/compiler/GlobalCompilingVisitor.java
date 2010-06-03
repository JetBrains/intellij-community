package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.filters.CompositeFilter;
import com.intellij.structuralsearch.impl.matcher.filters.LexicalNodesFilter;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchingHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.strategies.ExprMatchingStrategy;

import java.util.ArrayList;

/**
 * @author maxim
 */
public class GlobalCompilingVisitor {
  private CompileContext context;
  private final ArrayList<PsiElement> myLexicalNodes = new ArrayList<PsiElement>();

  //private final JavaCompilingVisitor myJavaVisitor = new JavaCompilingVisitor(this);
  //private final PsiElementVisitor myXmlVisitor = new XmlCompilingVisitor(this);

  private int myCodeBlockLevel;

  private static final NodeFilter ourFilter = LexicalNodesFilter.getInstance();

  public static NodeFilter getFilter() {
    return ourFilter;
  }

  public void setHandler(PsiElement element, MatchingHandler handler) {
    MatchingHandler realHandler = context.getPattern().getHandlerSimple(element);

    if (realHandler instanceof SubstitutionHandler) {
      ((SubstitutionHandler)realHandler).setMatchHandler(handler);
    }
    else {
      // @todo care about composite handler in this case of simple handler!
      context.getPattern().setHandler(element, handler);
    }
  }

  public final void handle(PsiElement element) {

    if ((!ourFilter.accepts(element) ||
         element instanceof PsiIdentifier) &&
        (context.getPattern().isRealTypedVar(element)) &&
        context.getPattern().getHandlerSimple(element) == null
      ) {
      String name = SubstitutionHandler.getTypedVarString(element);
      // name is the same for named element (clazz,methods, etc) and token (name of ... itself)
      // @todo need fix this
      final SubstitutionHandler handler;

      context.getPattern().setHandler(
        element,
        handler = (SubstitutionHandler)context.getPattern().getHandler(name)
      );

      if (handler != null && context.getOptions().getVariableConstraint(handler.getName()).isPartOfSearchResults()) {
        handler.setTarget(true);
        context.getPattern().setTargetNode(element);
      }
    }
  }

  public CompileContext getContext() {
    return context;
  }

  public int getCodeBlockLevel() {
    return myCodeBlockLevel;
  }

  public void setCodeBlockLevel(int codeBlockLevel) {
    this.myCodeBlockLevel = codeBlockLevel;
  }

  static void setFilter(MatchingHandler handler, NodeFilter filter) {
    if (handler.getFilter() != null &&
        handler.getFilter().getClass() != filter.getClass()
      ) {
      // for constructor we will have the same handler for class and method and tokens itselfa
      handler.setFilter(
        new CompositeFilter(
          filter,
          handler.getFilter()
        )
      );
    }
    else {
      handler.setFilter(filter);
    }
  }

  public ArrayList<PsiElement> getLexicalNodes() {
    return myLexicalNodes;
  }

  public void addLexicalNode(PsiElement node) {
    myLexicalNodes.add(node);
  }

  void compile(PsiElement element, CompileContext context) {
    myCodeBlockLevel = 0;
    this.context = context;
    /*if (element instanceof XmlElement) {
      element.accept(myXmlVisitor);
    }
    else {
      element.accept(myJavaVisitor);
    }*/
    StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(element);
    assert profile != null;
    profile.compile(element, this);

    if (context.getPattern().getStrategy() == null) {
      context.getPattern().setStrategy(ExprMatchingStrategy.getInstance());
    }
  }
}
