/*
 * Copyright 2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath.validation;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.EmptyResolveMessageProvider;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.xpath.XPath2TokenTypes;
import org.intellij.lang.xpath.XPathElementType;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.context.VariableContext;
import org.intellij.lang.xpath.context.XPathVersion;
import org.intellij.lang.xpath.context.functions.Function;
import org.intellij.lang.xpath.context.functions.Parameter;
import org.intellij.lang.xpath.psi.*;
import org.intellij.lang.xpath.psi.impl.PrefixedNameImpl;
import org.intellij.lang.xpath.psi.impl.XPathChangeUtil;
import org.intellij.lang.xpath.psi.impl.XPathNumberImpl;
import org.intellij.lang.xpath.xslt.impl.XsltReferenceContributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.namespace.QName;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class XPathAnnotator extends XPath2ElementVisitor implements Annotator {

  private AnnotationHolder myHolder;

  public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder holder) {

    try {
      myHolder = holder;
      psiElement.accept(this);
    } finally {
      myHolder = null;
    }
  }

  @Override
  public void visitXPathNodeTest(XPathNodeTest o) {
    final ContextProvider contextProvider = o.getXPathContext();
    checkNodeTest(contextProvider, myHolder, o);
  }

  @Override
  public void visitXPathStep(XPathStep o) {
    checkSillyStep(myHolder, o);
    super.visitXPathStep(o);
  }

  @Override
  public void visitXPathNodeTypeTest(XPathNodeTypeTest o) {
    checkNodeTypeTest(myHolder, o);
    visitXPathExpression(o);
  }

  @Override
  public void visitXPathFunctionCall(XPathFunctionCall o) {
    final ContextProvider contextProvider = o.getXPathContext();
    checkFunctionCall(myHolder, o, contextProvider);
    super.visitXPathFunctionCall(o);
  }

  @Override
  public void visitXPathString(XPathString o) {
    checkString(myHolder, o);
    super.visitXPathString(o);
  }

  @Override
  public void visitXPathVariableReference(XPathVariableReference o) {
    final ContextProvider contextProvider = o.getXPathContext();
    checkVariableReference(myHolder, o, contextProvider);
    super.visitXPathVariableReference(o);
  }

  @Override
  public void visitXPath2TypeElement(XPath2TypeElement o) {
    final ContextProvider contextProvider = o.getXPathContext();
    checkPrefixReferences(myHolder, o, contextProvider);

    if (o.getDeclaredType() == XPathType.UNKNOWN) {
      final PsiReference[] references = o.getReferences();
      for (PsiReference reference : references) {
        if (reference instanceof XsltReferenceContributor.SchemaTypeReference ) {
          if (!reference.isSoft() && reference.resolve() == null) {
            final String message = ((EmptyResolveMessageProvider)reference).getUnresolvedMessagePattern();
            final Annotation annotation =
              myHolder.createErrorAnnotation(reference.getRangeInElement().shiftRight(o.getTextOffset()), message);
            annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
          }
        }
      }
    }
    super.visitXPath2TypeElement(o);
  }

  @Override
  public void visitXPathNumber(XPathNumber number) {
    if (number.getXPathVersion() == XPathVersion.V2) {

      final PsiElement leaf = PsiTreeUtil.nextLeaf(number);
      if (leaf != null) {
        final IElementType elementType = leaf.getNode().getElementType();
        if (elementType != XPathTokenTypes.STAR && XPath2TokenTypes.KEYWORDS.contains(elementType)) {
          final TextRange range = TextRange.create(number.getTextRange().getStartOffset(), leaf.getTextRange().getEndOffset());
          final Annotation annotation =
            myHolder.createErrorAnnotation(range, "Number literal must be followed by whitespace in XPath 2");

          final XPathBinaryExpression expression = PsiTreeUtil.getParentOfType(number, XPathBinaryExpression.class, true);
          if (expression != null) {
            final XPathExpression lOperand = expression.getLOperand();
            if (number == lOperand) {
              final XPathExpression rOperand = expression.getROperand();
              if (rOperand != null) {
                final String display = number.getText() + " " + expression.getOperationSign();
                final String replacement = display + " " + rOperand.getText();

                assert PsiEquivalenceUtil.areElementsEquivalent(expression, XPathChangeUtil.createExpression(expression, replacement));
                annotation.registerFix(new ExpressionReplacementFix(replacement, display, expression));
              }
            } else if (number == expression.getROperand()) {
              final PsiElement next = PsiTreeUtil.getParentOfType(PsiTreeUtil.nextLeaf(expression), XPathExpression.class, true);
              if (next instanceof XPathBinaryExpression) {
                final XPathBinaryExpression left = (XPathBinaryExpression)next;
                final XPathExpression rOperand = left.getROperand();
                if (rOperand != null && lOperand != null) {
                  final String display = number.getText() + " " + left.getOperationSign();
                  final String replacement = lOperand.getText() + " " + expression.getOperationSign() + " " + display + " " + rOperand.getText();

                  assert PsiEquivalenceUtil.areElementsEquivalent(next, XPathChangeUtil.createExpression(next, replacement));
                  annotation.registerFix(new ExpressionReplacementFix(replacement, display, (XPathExpression)next));
                }
              }
            }
          }
        }
      }
    } else {
      if (((XPathNumberImpl)number).isScientificNotation()) {
        myHolder.createErrorAnnotation(number, "Number literals in scientific notation are not allowed in XPath 1.0");
      }
    }

    super.visitXPathNumber(number);
  }

  @Override
  public void visitXPathBinaryExpression(final XPathBinaryExpression o) {
    final XPathExpression operand = o.getLOperand();
    if (operand != null && o.getXPathVersion() == XPathVersion.V2) {
      final XPathElementType operator = o.getOperator();

      if (XPath2TokenTypes.COMP_OPS.contains(operator)) {
        if (operand instanceof XPathBinaryExpression && XPath2TokenTypes.COMP_OPS.contains(((XPathBinaryExpression)operand).getOperator())) {
          final Annotation annotation = myHolder.createErrorAnnotation(o, "Consecutive comparison is not allowed in XPath 2");

          final XPathExpression rOperand = o.getROperand();
          if (rOperand != null) {
            final String replacement = "(" + operand.getText() + ") " + o.getOperationSign() + " " + rOperand.getText();
            annotation.registerFix(new ExpressionReplacementFix(replacement, o));
          }
        }

        if (XPath2TokenTypes.NODE_COMP_OPS.contains(operator)) {
          checkApplicability(o, XPath2Type.NODE);
        } else if (operator == XPath2TokenTypes.WEQ || operator == XPath2TokenTypes.WNE || operator == XPathTokenTypes.EQ || operator == XPathTokenTypes.NE) {
          checkApplicability(o,
                             XPath2Type.NUMERIC,XPath2Type.BOOLEAN,XPath2Type.STRING,
                             XPath2Type.DATE,XPath2Type.TIME,XPath2Type.DATETIME,XPath2Type.DURATION,
                             XPath2Type.HEXBINARY,XPath2Type.BASE64BINARY,XPath2Type.ANYURI,XPath2Type.QNAME);
        } else if (operator == XPath2TokenTypes.WGT || operator == XPath2TokenTypes.WGE || operator == XPath2TokenTypes.WLE || operator == XPath2TokenTypes.WLT ||
                   operator == XPathTokenTypes.GT || operator == XPathTokenTypes.GE || operator == XPathTokenTypes.LE || operator == XPathTokenTypes.LT) {
          checkApplicability(o,
                             XPath2Type.NUMERIC,XPath2Type.BOOLEAN,XPath2Type.STRING,
                             XPath2Type.DATE,XPath2Type.TIME,XPath2Type.DATETIME,XPath2Type.DURATION,
                             XPath2Type.ANYURI);
        }
      } else if (XPath2TokenTypes.UNION_OPS.contains(operator) || XPath2TokenTypes.INTERSECT_EXCEPT.contains(operator)) {
        checkApplicability(o, XPath2SequenceType.create(XPath2Type.NODE, XPath2SequenceType.Cardinality.ZERO_OR_MORE));
      } else if (operator == XPath2TokenTypes.TO) {
        checkApplicability(o, XPath2Type.INTEGER);
      } else if (operator == XPathTokenTypes.AND || operator == XPathTokenTypes.OR) {
        checkApplicability(o, XPath2Type.BOOLEAN);
      } else if (XPathTokenTypes.ADD_OPS.contains(operator)) {
        checkApplicability(o, XPath2Type.NUMERIC, XPath2Type.DURATION, XPath2Type.DATE, XPath2Type.TIME, XPath2Type.DATETIME);
      } else if (XPath2TokenTypes.MULT_OPS.contains(operator)) {
        if (operator == XPath2TokenTypes.IDIV || operator == XPathTokenTypes.MOD) {
          checkApplicability(o, XPath2Type.NUMERIC);
        } else if (operator == XPathTokenTypes.DIV) {
          checkApplicability(o, XPath2Type.NUMERIC, XPath2Type.DURATION);
        } else {
          assert operator == XPathTokenTypes.MULT;
          checkApplicability(o, XPath2Type.NUMERIC, XPath2Type.DURATION);
        }
      }
    }

    checkExpression(myHolder, o);
    super.visitXPathBinaryExpression(o);
  }

  private void checkApplicability(XPathBinaryExpression o, XPath2Type... applicableTypes) {
    final XPathExpression lOperand = o.getLOperand();
    assert lOperand != null;

    final XPathType leftType = XPath2Type.mapType(lOperand.getType());
    for (XPath2Type applicableType : applicableTypes) {
      if (XPathType.isAssignable(applicableType, leftType)) {
        return;
      }
    }
    myHolder.createErrorAnnotation(o, "Operator '" + o.getOperationSign() + "' cannot be applied to expressions of type '" + leftType.getName() + "'");
  }

  @Override
  public void visitXPathExpression(XPathExpression o) {
    checkExpression(myHolder, o);
  }

  private static void checkString(AnnotationHolder holder, XPathString string) {
    if (!string.isWellFormed()) {
      holder.createErrorAnnotation(string, "Malformed string literal");
    }
  }

  private static void checkVariableReference(AnnotationHolder holder, XPathVariableReference reference, @NotNull ContextProvider contextProvider) {
    if (reference.resolve() == null) {
      final VariableContext variableResolver = contextProvider.getVariableContext();
      if (variableResolver == null) return;

      if (!variableResolver.canResolve()) {
        final Object[] variablesInScope = variableResolver.getVariablesInScope(reference);
        if (variablesInScope instanceof String[]) {
          final Set<String> variables = new HashSet<>(Arrays.asList((String[])variablesInScope));
          if (!variables.contains(reference.getReferencedName())) {
            markUnresolvedVariable(reference, holder);
          }
        } else if (variablesInScope instanceof QName[]) {
          final Set<QName> variables = new HashSet<>(Arrays.asList((QName[])variablesInScope));
          if (!variables.contains(contextProvider.getQName(reference))) {
            markUnresolvedVariable(reference, holder);
          }
        }
      } else {
        markUnresolvedVariable(reference, holder);
      }
    }
  }

  private static void markUnresolvedVariable(XPathVariableReference reference, AnnotationHolder holder) {
    final String referencedName = reference.getReferencedName();
    // missing name is already flagged by parser
    if (referencedName.length() > 0) {
      final TextRange range = reference.getTextRange().shiftRight(1).grown(-1);
      final Annotation ann = holder.createErrorAnnotation(range, "Unresolved variable '" + referencedName + "'");
      ann.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      final VariableContext variableContext = ContextProvider.getContextProvider(reference).getVariableContext();
      if (variableContext != null) {
        final IntentionAction[] fixes = variableContext.getUnresolvedVariableFixes(reference);
        for (IntentionAction fix : fixes) {
          ann.registerFix(fix);
        }
      }
    }
  }

  private static void checkSillyStep(AnnotationHolder holder, XPathStep step) {
    final XPathExpression previousStep = step.getPreviousStep();
    if (previousStep instanceof XPathStep) {
      final XPathNodeTest nodeTest = ((XPathStep)previousStep).getNodeTest();
      if (nodeTest != null) {
        final XPathNodeTest.PrincipalType principalType = nodeTest.getPrincipalType();
        if (principalType != XPathNodeTest.PrincipalType.ELEMENT) {
          XPathNodeTest test = step.getNodeTest();
          if (test != null) {
            holder.createWarningAnnotation(test, "Silly location step on " + principalType.getType() + " axis");
          }
        }
      }
    }
  }

  private static void checkFunctionCall(AnnotationHolder holder, XPathFunctionCall call, @NotNull ContextProvider contextProvider) {
    final ASTNode node = call.getNode().findChildByType(XPathTokenTypes.FUNCTION_NAME);
    if (node == null) {
      return;
    }

    final QName name = contextProvider.getQName(call);
    final XPathFunction function = call.resolve();
    final Function functionDecl = function != null ? function.getDeclaration() : null;
    if (functionDecl == null) {
      final PrefixedNameImpl qName = (PrefixedNameImpl)call.getQName();

      // need special check for extension functions
      if (call.getQName().getPrefix() != null && contextProvider.getFunctionContext().allowsExtensions()) {
        final PsiReference[] references = call.getReferences();
        if (references.length > 1 && references[1].resolve() == null) {
          final Annotation ann = holder.createErrorAnnotation(qName.getPrefixNode(), "Extension namespace prefix '" +
                                                                                     qName.getPrefix() +
                                                                                     "' has not been declared");
          ann.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        } else if (name != null){
          final String extNS = name.getNamespaceURI();
          if (!StringUtil.isEmpty(extNS)) {
            final Set<Pair<QName,Integer>> pairs = contextProvider.getFunctionContext().getFunctions().keySet();
            for (Pair<QName, Integer> pair : pairs) {
              // extension namespace is known
              final String uri = pair.first.getNamespaceURI();
              if (uri != null && uri.equals(extNS)) {
                holder.createWarningAnnotation(node, "Unknown function '" + name + "'");
              }
            }
          }
        }
      } else {
        if (name != null) {
          holder.createWarningAnnotation(node, "Unknown function '" + name + "'");
        } else if (qName.getPrefixNode() != null) {
          final Annotation ann = holder.createErrorAnnotation(qName.getPrefixNode(), "Extension namespace prefix '" +
                                                                                     qName.getPrefix() +
                                                                                     "' has not been declared");
          ann.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        }
      }
    } else {
      final XPathExpression[] arguments = call.getArgumentList();
      for (int i = 0; i < arguments.length; i++) {
        checkArgument(holder, arguments[i], i, functionDecl.getParameters());
      }
      if (arguments.length < functionDecl.getMinArity()) {
        if (functionDecl.getMinArity() == 1) {
          holder.createErrorAnnotation(node, "Missing argument for function '" + name + "'");
        } else {
          final Parameter last = functionDecl.getParameters()[functionDecl.getParameters().length - 1];
          final String atLeast =
                  last.kind == Parameter.Kind.OPTIONAL ||
                          last.kind == Parameter.Kind.VARARG ?
                          "at least " : "";
          holder.createErrorAnnotation(node, "Function '" + name + "' requires " + atLeast + functionDecl.getMinArity() + " arguments");
        }
      }
    }
  }

  private static void checkArgument(AnnotationHolder holder, XPathExpression argument, int i, Parameter[] parameters) {
    if (i >= parameters.length) {
      if (parameters.length > 0 && parameters[parameters.length - 1].kind == Parameter.Kind.VARARG) {
        // OK. Validate types against the last declared - vararg - param.
      } else {
        holder.createErrorAnnotation(argument, "Too many arguments");
      }
    }
  }

  private static void checkNodeTypeTest(AnnotationHolder holder, XPathNodeTypeTest test) {
    final NodeType nodeType = test.getNodeType();
    if (nodeType == null) {
      return;
    }

    final XPathExpression[] arguments = test.getArgumentList();
    if (test.getXPathVersion() == XPathVersion.V2) {

      switch (nodeType) {
        case NODE:
        case TEXT:
        case COMMENT:
          markExceedingArguments(holder, arguments, 0);
          break;

        // TODO: parser doesn't understand TypeName? yet:
        //   	 ElementTest 	   ::=    	"element" "(" (ElementNameOrWildcard ("," TypeName "?"?)?)? ")"
        case ELEMENT:
        case ATTRIBUTE:
          checkKindTestArguments(holder, test, true, 0, 2);
          break;

        case SCHEMA_ELEMENT:
        case SCHEMA_ATTRIBUTE:
          checkKindTestArguments(holder, test, false, 1, 1);
          break;

        case DOCUMENT_NODE:
          if (arguments.length >= 1) {
            markExceedingArguments(holder, arguments, 1);

            final XPathNodeTypeTest argument = findNodeType(arguments[0]);
            if (argument != null) {
              final NodeType type = argument.getNodeType();
              if (type == NodeType.ELEMENT || type == NodeType.SCHEMA_ELEMENT) {
                return;
              }
            }
            holder.createErrorAnnotation(arguments[0], "element() or schema-element() expected");
          }
          break;

        case PROCESSING_INSTRUCTION:
          if (arguments.length >= 1) {
            markExceedingArguments(holder, arguments, 1);

            if (arguments[0] instanceof XPathString) {
              break;
            } else {
              final PrefixedName argument = findQName(arguments[0]);
              if (argument != null) {
                if (argument.getPrefix() == null && !"*".equals(argument.getLocalName())) {
                  break;
                }
              }
            }
            holder.createErrorAnnotation(arguments[0], "String literal or NCName expected");
          }
          break;
      }
    } else {
      if (arguments.length == 0) {
        return;
      }
      if (test.getNodeType() == NodeType.PROCESSING_INSTRUCTION && arguments.length == 1) {
        if (!(arguments[0] instanceof XPathString)) {
          holder.createErrorAnnotation(arguments[0], "String literal expected");
        }
        return;
      }
      holder.createErrorAnnotation(test, "Invalid number of arguments for node type test '" + nodeType.getType() + "'");
    }
  }

  private static void checkKindTestArguments(AnnotationHolder holder,
                                             XPathNodeTypeTest test,
                                             boolean wildcardAllowed,
                                             int min,
                                             int max) {
    final XPathExpression[] arguments = test.getArgumentList();
    if (arguments.length >= min) {
      for (XPathExpression arg : arguments) {
        final PrefixedName argument = findQName(arg);
        if (argument == null) {
          holder.createErrorAnnotation(arg, "QName expected");
        } else {
          if (!wildcardAllowed && ("*".equals(argument.getPrefix()) || "*".equals(argument.getLocalName()))) {
            holder.createErrorAnnotation(arg, "QName expected");
          }
        }
      }
    } else {
      holder.createErrorAnnotation(test, "Missing argument for node kind test");
    }

    markExceedingArguments(holder, arguments, max);
  }

  private static void markExceedingArguments(AnnotationHolder holder, XPathExpression[] arguments, int start) {
    for (int i = start; i < arguments.length; i++) {
      holder.createErrorAnnotation(arguments[i], "Too many arguments");
    }
  }

  @Nullable
  private static XPathNodeTypeTest findNodeType(XPathExpression argument) {
    final XPathNodeTest test = findNodeTest(argument);
    if (test != null && !test.isNameTest() && test.getPrincipalType() == XPathNodeTest.PrincipalType.ELEMENT) {
      return PsiTreeUtil.getChildOfType(test, XPathNodeTypeTest.class);
    }
    return null;
  }

  @Nullable
  private static PrefixedName findQName(XPathExpression argument) {
    final XPathNodeTest test = findNodeTest(argument);
    if (test != null && test.isNameTest() && test.getPrincipalType() == XPathNodeTest.PrincipalType.ELEMENT) {
      return test.getQName();
    }
    return null;
  }

  @Nullable
  private static XPathNodeTest findNodeTest(XPathExpression argument) {
    if (argument instanceof XPathLocationPath) {
      final XPathStep step = ((XPathLocationPath)argument).getFirstStep();
      if (step != null && step.getPreviousStep() == null && step.getPredicates().length == 0) {
        final XPathNodeTest test = step.getNodeTest();
        if (test != null) {
          return test;
        }
      }
    }
    return null;
  }

  private static void checkNodeTest(@NotNull ContextProvider myProvider, AnnotationHolder holder, XPathNodeTest nodeTest) {
    checkSillyNodeTest(holder, nodeTest);

    checkPrefixReferences(holder, nodeTest, myProvider);
  }

  private static void checkPrefixReferences(AnnotationHolder holder, QNameElement element, ContextProvider myProvider) {
    final PsiReference[] references = element.getReferences();
    for (PsiReference reference : references) {
      if (reference instanceof PrefixReference) {
        final PrefixReference pr = ((PrefixReference)reference);
        if (!pr.isSoft() && pr.isUnresolved()) {
          final TextRange range = pr.getRangeInElement().shiftRight(pr.getElement().getTextRange().getStartOffset());
          final Annotation a = holder.createErrorAnnotation(range, "Unresolved namespace prefix '" + pr.getCanonicalText() + "'");
          a.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);

          final NamespaceContext namespaceContext = myProvider.getNamespaceContext();
          final PrefixedName qName = element.getQName();
          if (namespaceContext != null && qName != null) {
            final IntentionAction[] fixes = namespaceContext.getUnresolvedNamespaceFixes(reference, qName.getLocalName());
            for (IntentionAction fix : fixes) {
              a.registerFix(fix);
            }
          }
        }
      }
    }
  }

  private static void checkSillyNodeTest(AnnotationHolder holder, XPathNodeTest nodeTest) {
    if (nodeTest.getPrincipalType() != XPathNodeTest.PrincipalType.ELEMENT) {
      final XPathNodeTypeTest typeTest = PsiTreeUtil.getChildOfType(nodeTest, XPathNodeTypeTest.class);
      if (typeTest != null) {
        holder.createWarningAnnotation(typeTest, "Silly node type test on axis '" + nodeTest.getPrincipalType().getType() + "'");
      }
    }
  }

  @Override
  public void visitXPathPredicate(XPathPredicate o) {
    final XPathExpression expression = o.getPredicateExpression();
    if (expression instanceof XPathLocationPath) {
      final XPathExpression parentOfType = PsiTreeUtil.getParentOfType(o, XPathExpression.class, true);
      if (parentOfType != null && XPath2Type.ANYATOMICTYPE.isAssignableFrom(parentOfType.getType())) {
        myHolder.createErrorAnnotation(expression, "Axis step cannot be used here: the context item is an atomic value");
      }
    }
    super.visitXPathPredicate(o);
  }

  private static void checkExpression(AnnotationHolder holder, @NotNull XPathExpression expression) {
    final XPathType expectedType = ExpectedTypeUtil.getExpectedType(expression);
    final XPathType opType = ExpectedTypeUtil.mapType(expression, expression.getType());
    if (!XPathType.isAssignable(expectedType, opType)) {
      holder.createErrorAnnotation(expression, "Expected type '" + expectedType.getName() + "', got '" + opType.getName() + "'");
    }
  }
}