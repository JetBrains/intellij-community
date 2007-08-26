package com.intellij.structuralsearch.impl.matcher;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.impl.matcher.handlers.Handler;
import com.intellij.structuralsearch.impl.matcher.handlers.SimpleHandler;
import com.intellij.structuralsearch.impl.matcher.handlers.SubstitutionHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy;

import java.util.HashMap;

/**
 * Class to hold compiled pattern information
 */
public abstract class CompiledPattern {
  private boolean requestsSuperFields;
  private boolean requestsSuperMethods;
  private boolean requestsSuperInners;
  private SearchScope scope;
  private NodeIterator nodes;
  private MatchingStrategy strategy;
  private PsiElement targetNode;

  // @todo support this property during matching (how many nodes should be advanced)
  // when match is not successfull (or successful partially)
  //private int advancement = 1;

  public abstract String getTypedVarPrefix();
  public abstract boolean isTypedVar(String str);

  public void setTargetNode(final PsiElement element) {
    targetNode = element;
  }

  public PsiElement getTargetNode() {
    return targetNode;
  }

  public static class JavaCompiledPattern extends CompiledPattern {
    public String getTypedVarPrefix() {
      return TYPED_VAR_PREFIX;
    }

    public boolean isTypedVar(final String str) {
      if (str.charAt(0)=='@') {
        return str.regionMatches(1,TYPED_VAR_PREFIX,0,TYPED_VAR_PREFIX.length());
      } else {
        return str.startsWith(TYPED_VAR_PREFIX);
      }
    }
  }

  public static class XmlCompiledPattern extends CompiledPattern {
    public String getTypedVarPrefix() {
      return XML_TYPED_VAR_PREFIX;
    }

    public boolean isTypedVar(final String str) {
      return str.startsWith(XML_TYPED_VAR_PREFIX);
    }
  }

  private static final String TYPED_VAR_PREFIX = "__$_";
  private static final String XML_TYPED_VAR_PREFIX = "__";
  public static final Key<PsiElement> ALL_CLASS_CONTENT_VAR_KEY = Key.create("AllClassContent");
  public static final Key<String> FQN = Key.create("FQN");
  public static final Key<Object> HANDLER_KEY = Key.create("ss.handler");

  MatchingStrategy getStrategy() {
    return strategy;
  }

  public void setStrategy(MatchingStrategy strategy) {
    this.strategy = strategy;
  }

  public NodeIterator getNodes() {
    return nodes;
  }

  public void setNodes(NodeIterator nodes) {
    this.nodes = nodes;
  }

  boolean isTypedVar(final PsiElement element) {
    return element!=null && isTypedVar( element.getText() );
  }

  public boolean isRealTypedVar(PsiElement element) {
    if (element!=null && element.getTextLength()>0) {
      String str = SubstitutionHandler.getTypedVarString(element);
      if (str.length()==0) {
        return false;
      }
      return isTypedVar( str );
    } else {
      return false;
    }
  }

  private HashMap<Object,Handler> handlers = new HashMap<Object,Handler>();

  public Handler getHandlerSimple(PsiElement node) {
    return handlers.get(node);
  }

  private PsiElement last;
  private Handler lastHandler;

  public Handler getHandler(PsiElement node) {
    if (node==last) {
      return lastHandler;
    }
    Handler handler = handlers.get(node);

    if (handler==null) {
      handler = new SimpleHandler();
      setHandler(node,handler);
    }

    last = node;
    lastHandler = handler;

    return handler;
  }

  public Handler getHandler(String name) {
    return handlers.get(name);
  }

  public void setHandler(PsiElement node, Handler handler) {
    handlers.put(node,handler);
  }

  public SubstitutionHandler createSubstitutionHandler(
    String name, String compiledName, boolean target,int minOccurs, int maxOccurs, boolean greedy) {

    SubstitutionHandler handler = (SubstitutionHandler) handlers.get(compiledName);
    if (handler != null) return handler;

    handler = new SubstitutionHandler(
      name,
      target,
      minOccurs,
      maxOccurs,
      greedy
    );

    handlers.put(compiledName,handler);

    return handler;
  }

  public boolean isRequestsSuperFields() {
    return requestsSuperFields;
  }

  public void setRequestsSuperFields(boolean requestsSuperFields) {
    this.requestsSuperFields = requestsSuperFields;
  }

  public boolean isRequestsSuperInners() {
    return requestsSuperInners;
  }

  public void setRequestsSuperInners(boolean requestsSuperInners) {
    this.requestsSuperInners = requestsSuperInners;
  }

  public boolean isRequestsSuperMethods() {
    return requestsSuperMethods;
  }

  public void setRequestsSuperMethods(boolean requestsSuperMethods) {
    this.requestsSuperMethods = requestsSuperMethods;
  }

  public SearchScope getScope() {
    return scope;
  }

  public void setScope(SearchScope scope) {
    this.scope = scope;
  }

  void clearHandlersState() {
    for (final Handler h : handlers.values()) {
      if (h != null) h.reset();
    }
  }
}
