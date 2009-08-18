package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Jul 23, 2009
 * Time: 4:15:10 PM
 */
public class PyStringFormatInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.str.format");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "PyStringFormatInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new Visitor(holder);
  }

  public static class Visitor extends PyInspectionVisitor {
    private static class Inspection {
      private static final String FORMAT_FLAGS = "#0- +";
      private static final String FORMAT_LENGTH = "hlL";
      private static final Map<Character, String> FORMAT_CONVERSIONS = new HashMap<Character, String>();

      static {
        FORMAT_CONVERSIONS.put('d', "int");
        FORMAT_CONVERSIONS.put('i', "int");
        FORMAT_CONVERSIONS.put('o', "int");
        FORMAT_CONVERSIONS.put('u', "int");
        FORMAT_CONVERSIONS.put('x', "int");
        FORMAT_CONVERSIONS.put('X', "int");
        FORMAT_CONVERSIONS.put('e', "float");
        FORMAT_CONVERSIONS.put('E', "float");
        FORMAT_CONVERSIONS.put('f', "float");
        FORMAT_CONVERSIONS.put('F', "float");
        FORMAT_CONVERSIONS.put('g', "float");
        FORMAT_CONVERSIONS.put('G', "float");
        FORMAT_CONVERSIONS.put('c', "str");
        FORMAT_CONVERSIONS.put('r', "str");
        FORMAT_CONVERSIONS.put('s', "str");
      }

      private final Map<String, Boolean> myUsedMappingKeys = new HashMap<String, Boolean>();
      private int myExpectedArguments = 0;
      private boolean myProblemRegister = false;
      private final Visitor myVisitor;

      private final Map<String, String> myFormatSpec = new HashMap<String, String>();

      public Inspection(Visitor visitor) {
        myVisitor = visitor;
      }

      private int inspectArguments(@Nullable final PyExpression rightExpression) {
        final Class[] SIMPLE_RHS_EXPRESSIONS = {
          PyLiteralExpression.class, PyReferenceExpression.class, PyCallExpression.class, PySubscriptionExpression.class
        };

        if (PyUtil.instanceOf(rightExpression, SIMPLE_RHS_EXPRESSIONS))
        {
          if (myFormatSpec.get("1") != null) {
            checkType(rightExpression, myFormatSpec.get("1"));
          }
          return 1;
        }
        else if (rightExpression instanceof PyParenthesizedExpression) {
          return inspectArguments(((PyParenthesizedExpression)rightExpression).getContainedExpression());
        }
        else if (rightExpression instanceof PyTupleExpression) {
          final PyExpression[] expressions = ((PyTupleExpression)rightExpression).getElements();
          int i = 1;
          for (PyExpression expression : expressions) {
            final String formatSpec = myFormatSpec.get(Integer.toString(i));
            if (formatSpec != null) {
              checkType(expression, formatSpec);
            }
            ++i;
          }
          return expressions.length;
        }
        else if (rightExpression instanceof PyDictLiteralExpression) {
          final PyKeyValueExpression[] expressions = ((PyDictLiteralExpression)rightExpression).getElements();
          if (expressions == null) {
            return 0;
          }
          if (myUsedMappingKeys.isEmpty() && expressions.length != 0) {
            registerProblem(rightExpression, "Format doesn't require a mapping");
          }
          for (PyKeyValueExpression expression : expressions) {
            final PyExpression key = expression.getKey();
            if (key instanceof PyStringLiteralExpression) {
              final String name = ((PyStringLiteralExpression)key).getStringValue();
              if (myUsedMappingKeys.get(name) != null) {
                myUsedMappingKeys.put(name, true);
                final PyExpression value = expression.getValue();
                if (value != null) {
                  checkType(value, myFormatSpec.get(name));
                }
              }
            }
          }
          if (myUsedMappingKeys.containsValue(false)) {
            registerProblem(rightExpression, "One of keys has no following argument");
          }
          return expressions.length;
        }
        return 0;
      }

      private void registerProblem(@NotNull final PyExpression rightExpression, @NotNull final String message) {
        myProblemRegister = true;
        myVisitor.registerProblem(rightExpression, message);
      }

      private void checkType(@NotNull final PyExpression expression, @NotNull final String expextedTypeName) {
        final PyType type = expression.getType();
        if (type != null) {
          final String typeName = type.getName();
          if (!"str".equals(typeName)) {
            return;
          }
          if (!expextedTypeName.equals(typeName)) {
            registerProblem(expression, "Unexpected type");
          }
        }
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
              registerProblem(formatExpression, "Too few mapping keys");
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
          registerProblem(formatExpression, "There are no format specifier character");
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
          final PyType type = rightExpression.getType();
          if (type != null) {
            if (myUsedMappingKeys.size() > 0 && !("dict".equals(type.getName()))) {
              registerProblem(rightExpression, "Format requires a mapping");
              return;
            }
          }
          inspectArgumentsNumber(rightExpression);
        }
      }

      private void inspectArgumentsNumber(@NotNull final PyExpression rightExpression) {
        final int arguments = inspectArguments(rightExpression);
        if (myUsedMappingKeys.isEmpty()) {
          if (myExpectedArguments < arguments) {
            registerProblem(rightExpression, "Too many arguments for format string");
          }
          else if (myExpectedArguments > arguments) {
            registerProblem(rightExpression, "Too few arguments for format string");
          }
        }
      }
    }

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @Override
    public void visitPyBinaryExpression(final PyBinaryExpression node) {
      if (node.getLeftExpression() instanceof PyStringLiteralExpression && node.isOperator("%")) {
        final Inspection inspection = new Inspection(this);
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
