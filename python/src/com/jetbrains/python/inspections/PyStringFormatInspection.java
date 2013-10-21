/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.inspections;

import com.google.common.collect.ImmutableMap;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.jetbrains.python.inspections.PyStringFormatParser.filterSubstitutions;
import static com.jetbrains.python.inspections.PyStringFormatParser.parsePercentFormat;

/**
 * @author Alexey.Ivanov
 */
public class PyStringFormatInspection extends PyInspection {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.inspections.PyStringFormatInspection");

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.str.format");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    private static class Inspection {
      private static final ImmutableMap<Character, String> FORMAT_CONVERSIONS = ImmutableMap.<Character, String>builder()
        .put('d', "int or long or float")
        .put('i', "int or long or float")
        .put('o', "int or long or float")
        .put('u', "int or long or float")
        .put('x', "int or long or float")
        .put('X', "int or long or float")
        .put('e', "float")
        .put('E', "float")
        .put('f', "float")
        .put('F', "float")
        .put('g', "float")
        .put('G', "float")
        .put('c', "str")
        .put('r', "str")
        .put('s', "str")
        .build();

      private final Map<String, Boolean> myUsedMappingKeys = new HashMap<String, Boolean>();
      private int myExpectedArguments = 0;
      private boolean myProblemRegister = false;
      private final Visitor myVisitor;
      private final TypeEvalContext myTypeEvalContext;

      private final Map<String, String> myFormatSpec = new HashMap<String, String>();

      public Inspection(Visitor visitor, TypeEvalContext typeEvalContext) {
        myVisitor = visitor;
        myTypeEvalContext = typeEvalContext;
      }

      // return number of arguments or -1 if it can not be computed
      private int inspectArguments(@Nullable final PyExpression rightExpression, final PsiElement problemTarget) {
        final Class[] SIMPLE_RHS_EXPRESSIONS = {
          PyLiteralExpression.class, PySubscriptionExpression.class, PyBinaryExpression.class, PyConditionalExpression.class
        };
        final Class[] LIST_LIKE_EXPRESSIONS = {PyListLiteralExpression.class, PyListCompExpression.class};
        final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(problemTarget);
        final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext);

        //in case tuple multiplies integer PY-4647
        if (rightExpression instanceof PyBinaryExpression) {
          PyBinaryExpression binaryExpression = (PyBinaryExpression)rightExpression;
          if (binaryExpression.getOperator() == PyTokenTypes.MULT && binaryExpression.getLeftExpression() instanceof PyParenthesizedExpression
                                                                && binaryExpression.getRightExpression() instanceof PyNumericLiteralExpression) {
            PyParenthesizedExpression parenthesizedExpression = (PyParenthesizedExpression)binaryExpression.getLeftExpression();
            if (parenthesizedExpression.getContainedExpression() instanceof PyTupleExpression) {
              PyExpression[] tupleElements = ((PyTupleExpression)parenthesizedExpression.getContainedExpression()).getElements();
              return ((PyNumericLiteralExpression)((PyBinaryExpression)rightExpression).getRightExpression()).getBigIntegerValue().intValue() * tupleElements.length;
            }
          }
        }
        final String s = myFormatSpec.get("1");
        if (PyUtil.instanceOf(rightExpression, SIMPLE_RHS_EXPRESSIONS)) {
          if (s != null) {
            assert rightExpression != null;
            PyType right_type = myTypeEvalContext.getType(rightExpression);
            if (right_type instanceof PySubscriptableType) {
              PySubscriptableType tuple_type = (PySubscriptableType)right_type;
              for (int i=0; i <= tuple_type.getElementCount(); i += 1) {
                PyType elementType = tuple_type.getElementType(i);
                if (elementType != null) {
                  final String typeName = myFormatSpec.get(String.valueOf(i + 1));
                  final PyType type = typeName != null ? PyTypeParser.getTypeByName(problemTarget, typeName) : null;
                  checkTypeCompatible(problemTarget, elementType, type);
                }
              }
              return tuple_type.getElementCount();
            }
            else checkExpressionType(rightExpression, s, problemTarget);
          }
          return 1;
        }
        else if (rightExpression instanceof PyReferenceExpression) {
          if (PyNames.DICT.equals(rightExpression.getName())) return -1;

          final PsiElement pyElement = ((PyReferenceExpression)rightExpression).followAssignmentsChain(resolveContext).getElement();
          if (pyElement == rightExpression || !(pyElement instanceof PyExpression)) {
            return -1;
          }
          if (pyElement instanceof PyDictLiteralExpression) {
            return inspectDict(rightExpression, problemTarget, true);
          }
          return inspectArguments((PyExpression)pyElement, problemTarget);
        }
        else if (rightExpression instanceof PyCallExpression) {
          final Callable callable = ((PyCallExpression)rightExpression).resolveCalleeFunction(resolveContext);
          // TODO: Switch to Callable.getReturnType()
          if (callable instanceof PyFunction && myTypeEvalContext.maySwitchToAST((PyFunction) callable)) {
            PyStatementList statementList = ((PyFunction)callable).getStatementList();
            if (statementList == null) {
              return -1;
            }
            PyReturnStatement[] returnStatements = PyUtil.getAllChildrenOfType(statementList, PyReturnStatement.class);
            int expressionsSize = -1;
            for (PyReturnStatement returnStatement : returnStatements) {
              if (returnStatement.getExpression() instanceof PyCallExpression) {
                return -1;
              }
              List<PyExpression> expressionList = PyUtil.flattenedParensAndTuples(returnStatement.getExpression());
              if (expressionsSize < 0) {
                expressionsSize = expressionList.size();
              }
              if (expressionsSize != expressionList.size()) {
                return -1;
              }
            }
            return expressionsSize;
          }
          return -1;
        }
        else if (rightExpression instanceof PyParenthesizedExpression) {
          final PyExpression rhs = ((PyParenthesizedExpression)rightExpression).getContainedExpression();
          return inspectArguments(rhs, rhs);
        }
        else if (rightExpression instanceof PyTupleExpression) {
          final PyExpression[] expressions = ((PyTupleExpression)rightExpression).getElements();
          int i = 1;
          for (PyExpression expression : expressions) {
            final String formatSpec = myFormatSpec.get(Integer.toString(i));
            if (formatSpec != null) {
              checkExpressionType(expression, formatSpec, expression);
            }
            ++i;
          }
          return expressions.length;
        }
        else if (rightExpression instanceof PyDictLiteralExpression) {
          return inspectDict(rightExpression, problemTarget, false);
        }
        else if (PyUtil.instanceOf(rightExpression, LIST_LIKE_EXPRESSIONS)) {
          if (s != null) {
            checkTypeCompatible(problemTarget, builtinCache.getStrType(),
                                PyTypeParser.getTypeByName(problemTarget, s));
            return 1;
          }
        }
        else if (rightExpression instanceof PySliceExpression && s != null) {
          final PyType type = myTypeEvalContext.getType(((PySliceExpression)rightExpression).getOperand());
          final PyType stringType = PyBuiltinCache.getInstance(rightExpression).getStringType(LanguageLevel.forElement(rightExpression));
          final PyType listType = PyBuiltinCache.getInstance(rightExpression).getListType();

          if (type == null || PyTypeChecker.match(listType, type, myTypeEvalContext)
              || PyTypeChecker.match(stringType, type, myTypeEvalContext)) {
            checkTypeCompatible(problemTarget, builtinCache.getStrType(),
                                PyTypeParser.getTypeByName(problemTarget, s));
            return 1;
          }
          PySliceItem sliceItem = ((PySliceExpression)rightExpression).getSliceItem();
          if (sliceItem != null) {
            PyExpression lower = sliceItem.getLowerBound();
            PyExpression upper = sliceItem.getUpperBound();
            PyExpression stride = sliceItem.getStride();
            if (upper instanceof PyNumericLiteralExpression) {
              BigInteger lowerVal;
              if (lower instanceof PyNumericLiteralExpression ) {
                lowerVal = ((PyNumericLiteralExpression)lower).getBigIntegerValue();
              }
              else {
                lowerVal = BigInteger.ZERO;
              }
              int count = (((PyNumericLiteralExpression)upper).getBigIntegerValue().subtract(lowerVal)).intValue();
              int strideVal;
              if (stride instanceof PyNumericLiteralExpression)
                strideVal = ((PyNumericLiteralExpression)stride).getBigIntegerValue().intValue();
              else
                strideVal = 1;
              int res = count/strideVal;
              int residue = count%strideVal == 0 ? 0 : 1;
              return res + residue;
            }
          }
          return -1;
        }
        return -1;
      }


      private static Map<PyExpression, PyExpression> addSubscriptions(PsiFile file, String operand) {
        Map<PyExpression, PyExpression> additionalExpressions = new HashMap<PyExpression,PyExpression>();
        PySubscriptionExpression[] subscriptionExpressions = PyUtil.getAllChildrenOfType(file, PySubscriptionExpression.class);
        for (PySubscriptionExpression expr : subscriptionExpressions) {
          if (expr.getOperand().getText().equals(operand)) {
            PsiElement parent = expr.getParent();
            if (parent instanceof PyAssignmentStatement) {
              if (expr.equals(((PyAssignmentStatement)parent).getLeftHandSideExpression())) {
                PyExpression key = expr.getIndexExpression();
                if (key != null) {
                  additionalExpressions.put(key, ((PyAssignmentStatement)parent).getAssignedValue());
                }
              }
            }
          }
        }
        return additionalExpressions;
      }

      // inspects dict expressions. Finds key-value pairs from subscriptions if addSubscriptions is true.
      private int inspectDict(PyExpression rightExpression, PsiElement problemTarget, boolean addSubscriptions) {
        PsiElement pyElement;
        Map<PyExpression, PyExpression> additionalExpressions;
        if (addSubscriptions) {
          additionalExpressions = addSubscriptions(rightExpression.getContainingFile(),
                                                                                 rightExpression.getText());
          pyElement = ((PyReferenceExpression)rightExpression).followAssignmentsChain(PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext)).getElement();
        }
        else {
          additionalExpressions = new HashMap<PyExpression,PyExpression>();
          pyElement = rightExpression;
        }

        final PyKeyValueExpression[] expressions = ((PyDictLiteralExpression)pyElement).getElements();
        if (myUsedMappingKeys.isEmpty()) {
          if (myExpectedArguments > 0) {
            if (myExpectedArguments == (expressions.length + additionalExpressions.size())) {
              // probably "%s %s" % {'a':1, 'b':2}, with names forgotten in template
              registerProblem(rightExpression, PyBundle.message("INSP.format.requires.no.mapping"));
            }
            else {
              // "braces: %s" % {'foo':1} gives "braces: {'foo':1}", implicit str() kicks in
              return 1;
            }
          }
          else {
            // "foo" % {whatever} is just "foo"
            return 0;
          }
        }
        for (PyKeyValueExpression expression : expressions) {
          final PyExpression key = expression.getKey();
          if (key instanceof PyStringLiteralExpression) {
            final String name = ((PyStringLiteralExpression)key).getStringValue();
            if (myUsedMappingKeys.get(name) != null) {
              myUsedMappingKeys.put(name, true);
              final PyExpression value = expression.getValue();
              if (value != null) {
                checkExpressionType(value, myFormatSpec.get(name), problemTarget);
              }
            }
          }
        }
        for (Map.Entry<PyExpression, PyExpression> expression : additionalExpressions.entrySet()) {
          final PyExpression key = expression.getKey();
          if (key instanceof PyStringLiteralExpression) {
            final String name = ((PyStringLiteralExpression)key).getStringValue();
            if (myUsedMappingKeys.get(name) != null) {
              myUsedMappingKeys.put(name, true);
              final PyExpression value = expression.getValue();
              if (value != null) {
                checkExpressionType(value, myFormatSpec.get(name), problemTarget);
              }
            }
          }
        }
        for (String key : myUsedMappingKeys.keySet()) {
          if (!myUsedMappingKeys.get(key).booleanValue()) {
            registerProblem(problemTarget, PyBundle.message("INSP.key.$0.has.no.arg", key));
            break;
          }
        }
        return (expressions.length + additionalExpressions.size());
      }

      private void registerProblem(@NotNull PsiElement problemTarget, @NotNull final String message) {
        myProblemRegister = true;
        myVisitor.registerProblem(problemTarget, message);
      }

      private void checkExpressionType(@NotNull final PyExpression expression, @NotNull final String expectedTypeName, PsiElement problemTarget) {
        final PyType actual = myTypeEvalContext.getType(expression);
        final PyType expected = PyTypeParser.getTypeByName(problemTarget, expectedTypeName);
        if (actual != null) {
          checkTypeCompatible(problemTarget, actual, expected);
        }
      }

      private void checkTypeCompatible(@NotNull final PsiElement problemTarget,
                                       @Nullable final PyType actual,
                                       @Nullable final PyType expected) {
        if (expected != null && "str".equals(expected.getName())) {
          return;
        }
        if (actual != null && !PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
          registerProblem(problemTarget, PyBundle.message("INSP.unexpected.type.$0", actual.getName()));
        }
      }

      private void inspectFormat(@NotNull final PyStringLiteralExpression formatExpression) {
        final String value = formatExpression.getStringValue();
        final List<PyStringFormatParser.SubstitutionChunk> chunks = filterSubstitutions(parsePercentFormat(value));

        // 1. The '%' character
        //  Skip the first item in the sections, it's always empty
        myExpectedArguments = chunks.size();
        myUsedMappingKeys.clear();

        // if use mapping keys
        final boolean mapping = chunks.size() > 0 && chunks.get(0).getMappingKey() != null;
        for (int i = 0; i < chunks.size(); ++i) {
          PyStringFormatParser.SubstitutionChunk chunk = chunks.get(i);

          // 2. Mapping key
          String mappingKey = Integer.toString(i+1);
          if (mapping) {
            if (chunk.getMappingKey() == null || chunk.isUnclosedMapping()) {
              registerProblem(formatExpression, PyBundle.message("INSP.too.few.keys"));
              break;
            }
            mappingKey = chunk.getMappingKey();
            myUsedMappingKeys.put(mappingKey, false);
          }

          // 4. Minimum field width
          inspectWidth(formatExpression, chunk.getWidth());

          // 5. Precision
          inspectWidth(formatExpression, chunk.getPrecision());

          // 7. Format specifier
          if (FORMAT_CONVERSIONS.containsKey(chunk.getConversionType())) {
            myFormatSpec.put(mappingKey, FORMAT_CONVERSIONS.get(chunk.getConversionType()));
            continue;
          }
          registerProblem(formatExpression, PyBundle.message("INSP.no.format.specifier.char"));
        }
      }

      private void inspectWidth(@NotNull final PyStringLiteralExpression formatExpression, String width) {
        if ("*".equals(width)){
          ++myExpectedArguments;
          if (myUsedMappingKeys.size() > 0) {
            registerProblem(formatExpression, "Can't use \'*\' in formats when using a mapping");
          }
        }
      }

      public boolean isProblem() {
        return myProblemRegister;
      }

      private void inspectValues(@Nullable final PyExpression rightExpression) {
        if (rightExpression == null) {
          return;
        }
        if (rightExpression instanceof PyParenthesizedExpression) {
          inspectValues(((PyParenthesizedExpression)rightExpression).getContainedExpression());
        }
        else {
          final PyType type = myTypeEvalContext.getType(rightExpression);
          if (type != null) {
            if (myUsedMappingKeys.size() > 0 && !("dict".equals(type.getName()))) {
              registerProblem(rightExpression, PyBundle.message("INSP.format.requires.mapping"));
              return;
            }
          }
          inspectArgumentsNumber(rightExpression);
        }
      }

      private void inspectArgumentsNumber(@NotNull final PyExpression rightExpression) {
        final int arguments = inspectArguments(rightExpression, rightExpression);
        if (myUsedMappingKeys.isEmpty() && arguments >= 0) {
          if (myExpectedArguments < arguments) {
            registerProblem(rightExpression, PyBundle.message("INSP.too.many.args.for.fmt.string"));
          }
          else if (myExpectedArguments > arguments) {
            registerProblem(rightExpression, PyBundle.message("INSP.too.few.args.for.fmt.string"));
          }
        }
      }
    }

    public Visitor(final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyBinaryExpression(final PyBinaryExpression node) {
      if (node.getLeftExpression() instanceof PyStringLiteralExpression && node.isOperator("%")) {
        final Inspection inspection = new Inspection(this, myTypeEvalContext);
        final PyStringLiteralExpression literalExpression = (PyStringLiteralExpression)node.getLeftExpression();
        inspection.inspectFormat(literalExpression);
        if (inspection.isProblem()) {
          return;
        }
        inspection.inspectValues(node.getRightExpression());
      }
    }
  }
}
