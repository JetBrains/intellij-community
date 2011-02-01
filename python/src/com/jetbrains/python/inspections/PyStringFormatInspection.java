package com.jetbrains.python.inspections;

import com.google.common.collect.ImmutableMap;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PySubscriptableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeReference;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author Alexey.Ivanov
 */
public class PyStringFormatInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.str.format");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly, LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    private static class Inspection {
      private static final String FORMAT_FLAGS = "#0- +";
      private static final String FORMAT_LENGTH = "hlL";
      private static final ImmutableMap<Character, String> FORMAT_CONVERSIONS = ImmutableMap.<Character, String>builder()
        .put('d', "int")
        .put('i', "int")
        .put('o', "int")
        .put('u', "int")
        .put('x', "int")
        .put('X', "int")
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

        final Class[] LIST_LIKE_EXPRESSIONS = {PyListLiteralExpression.class, PySliceExpression.class, PyListCompExpression.class};

        if (PyUtil.instanceOf(rightExpression, SIMPLE_RHS_EXPRESSIONS)) {
          if (myFormatSpec.get("1") != null) {
            assert rightExpression != null;
            PyType right_type = myTypeEvalContext.getType(rightExpression);
            if (right_type instanceof PySubscriptableType) {
              PySubscriptableType tuple_type = (PySubscriptableType)right_type;
              for (int i=0; i <= tuple_type.getElementCount(); i += 1) {
                PyType a_type = tuple_type.getElementType(i);
                if (a_type != null) {
                  checkTypeCompatible(problemTarget, a_type.getName(), myFormatSpec.get(String.valueOf(i+1)));
                }
              }
              return tuple_type.getElementCount();
            }
            else checkExpressionType(rightExpression, myFormatSpec.get("1"), problemTarget);
          }
          return 1;
        }
        else if (rightExpression instanceof PyReferenceExpression) {
          final PsiElement pyElement = ((PyReferenceExpression)rightExpression).followAssignmentsChain(myTypeEvalContext).getElement();
          if (!(pyElement instanceof PyExpression)) {
            return -1;
          }
          return inspectArguments((PyExpression)pyElement, problemTarget);
        }
        else if (rightExpression instanceof PyCallExpression) {
          final PyCallExpression.PyMarkedCallee markedFunction = ((PyCallExpression)rightExpression).resolveCallee(myTypeEvalContext);
          if (markedFunction != null && !markedFunction.isImplicitlyResolved()) {
            final Callable callable = markedFunction.getCallable();
            if (callable instanceof PyFunction && myTypeEvalContext.maySwitchToAST((PyFunction) callable)) {
              PyStatementList statementList = ((PyFunction)callable).getStatementList();
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
          final PyKeyValueExpression[] expressions = ((PyDictLiteralExpression)rightExpression).getElements();
          if (myUsedMappingKeys.isEmpty()) {
            if (myExpectedArguments > 0) {
              if (myExpectedArguments == expressions.length) {
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
          for (String key : myUsedMappingKeys.keySet()) {
            if (!myUsedMappingKeys.get(key).booleanValue()) {
              registerProblem(problemTarget, PyBundle.message("INSP.key.$0.has.no.arg", key));
              break;
            }
          }
          return expressions.length;
        }
        else if (PyUtil.instanceOf(rightExpression, LIST_LIKE_EXPRESSIONS)) {
          if (myFormatSpec.get("1") != null) {
            checkTypeCompatible(problemTarget, "str", myFormatSpec.get("1"));
            return 1;
          }
        }
        return -1;
      }

      private void registerProblem(@NotNull PsiElement problemTarget, @NotNull final String message) {
        myProblemRegister = true;
        myVisitor.registerProblem(problemTarget, message);
      }

      private void checkExpressionType(@NotNull final PyExpression expression, @NotNull final String expectedTypeName, PsiElement problemTarget) {
        final PyType type = myTypeEvalContext.getType(expression);
        if (type != null && !(type instanceof PyTypeReference)) {
          final String typeName = type.getName();
          checkTypeCompatible(problemTarget, typeName, expectedTypeName);
        }
      }

      private void checkTypeCompatible(@NotNull final PsiElement problemTarget,
                                       @Nullable final String typeName,
                                       @NotNull final String expectedTypeName) {
        if ("str".equals(expectedTypeName)) {
          return;
        }
        if ("int".equals(typeName) || "float".equals(typeName)) {
          return;
        }
        registerProblem(problemTarget, PyBundle.message("INSP.unexpected.type.$0", typeName));
      }

      private void inspectFormat(@NotNull final PyStringLiteralExpression formatExpression) {
        final String literal = formatExpression.getStringValue().replace("%%", "");

        // 1. The '%' character
        final String[] sections = literal.split("%");

        //  Skip the first item in the sections, it's always empty
        myExpectedArguments = sections.length - 1;
        myUsedMappingKeys.clear();

        // if use mapping keys
        final boolean mapping = (sections.length > 1 && sections[1].charAt(0) == '(');
        for (int i = 1; i < sections.length; ++i) {
          final String section = sections[i];
          int characterNumber = 0;

          // 2. Mapping key
          String mappingKey = Integer.toString(i);
          if (mapping) {
            characterNumber = section.indexOf(")");
            if (section.charAt(0) != '(' || characterNumber == -1) {
              registerProblem(formatExpression, PyBundle.message("INSP.too.few.keys"));
              break;
            }
            mappingKey = section.substring(1, characterNumber);
            myUsedMappingKeys.put(mappingKey, false);
            ++characterNumber; // Skip ')'
          }

          // 3. Conversions flags
          final int length = section.length();
          while (characterNumber < length && FORMAT_FLAGS.indexOf(section.charAt(characterNumber)) != -1) {
            ++characterNumber;
          }

          // 4. Minimum field width
          if (characterNumber < length) {
            characterNumber = inspectWidth(formatExpression, section, characterNumber);
          }

          // 5. Precision
          if (characterNumber < length && section.charAt(characterNumber) == '.') {
            ++characterNumber;
            characterNumber = inspectWidth(formatExpression, section, characterNumber);
          }

          // 6. Length modifier
          if (characterNumber < length && FORMAT_LENGTH.indexOf(section.charAt(characterNumber)) != -1) {
            ++characterNumber;
          }

          // 7. Format specifier
          if (characterNumber < length) {
            final char c = section.charAt(characterNumber);
            if (FORMAT_CONVERSIONS.containsKey(c)) {
              myFormatSpec.put(mappingKey, FORMAT_CONVERSIONS.get(c));
              continue;
            }
          }
          registerProblem(formatExpression, PyBundle.message("INSP.no.format.specifier.char"));
        }
      }

      private int inspectWidth(@NotNull final PyStringLiteralExpression formatExpression,
                               @NotNull final String section,
                               int characterNumber) {
        final int length = section.length();
        if (section.charAt(characterNumber) == '*') {
          ++myExpectedArguments;
          ++characterNumber;
          if (myUsedMappingKeys.size() > 0) {
            registerProblem(formatExpression, "Can't use \'*\' in formats when using a mapping");
          }
        }
        else {
          while (characterNumber < length && Character.isDigit(section.charAt(characterNumber))) {
            ++characterNumber;
          }
        }
        return characterNumber;
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
