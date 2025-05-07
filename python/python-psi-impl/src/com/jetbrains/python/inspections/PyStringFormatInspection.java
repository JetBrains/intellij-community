// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.google.common.collect.ImmutableMap;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyStringFormatParser;
import com.jetbrains.python.codeInsight.PySubstitutionChunkReference;
import com.jetbrains.python.inspections.quickfix.PyAddSpecifierToFormatQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedRatedResolveResult;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static com.jetbrains.python.PyStringFormatParser.*;
import static com.jetbrains.python.psi.PyUtil.as;

public final class PyStringFormatInspection extends PyInspection {

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder,
                                                 final boolean isOnTheFly,
                                                 @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  public static class Visitor extends PyInspectionVisitor {
    private static class Inspection {
      private static final ImmutableMap<Character, String> PERCENT_FORMAT_CONVERSIONS = ImmutableMap.<Character, String>builder()
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
        .put('a', "str")
        .put('s', "str")
        .put('b', "bytes")
        .build();

      private final Map<String, Boolean> myUsedMappingKeys = new HashMap<>();
      private int myExpectedArguments = 0;
      private boolean myProblemRegister = false;
      private final Visitor myVisitor;
      private final TypeEvalContext myTypeEvalContext;

      private final Map<String, String> myFormatSpec = new LinkedHashMap<>();

      Inspection(Visitor visitor, TypeEvalContext typeEvalContext) {
        myVisitor = visitor;
        myTypeEvalContext = typeEvalContext;
      }

      // return number of arguments or -1 if it can not be computed
      private int inspectArguments(final @Nullable PyExpression rightExpression, final @NotNull PsiElement problemTarget) {
        final Class[] SIMPLE_RHS_EXPRESSIONS = {
          PyLiteralExpression.class, PySubscriptionExpression.class, PyBinaryExpression.class, PyConditionalExpression.class
        };
        final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(problemTarget);
        final PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);

        final String s = myFormatSpec.get("1");
        if (PsiTreeUtil.instanceOf(rightExpression, SIMPLE_RHS_EXPRESSIONS)) {
          if (s != null) {
            final PyType rightType = myTypeEvalContext.getType(rightExpression);
            if (rightType instanceof PyTupleType tupleType) {
              matchEntireTupleTypes(problemTarget, tupleType);
              return tupleType.getElementCount();
            }
            else {
              checkExpressionType(rightExpression, s, problemTarget);
            }
          }
          return 1;
        }
        else if (rightExpression instanceof PyReferenceExpression) {
          if (PyNames.DUNDER_DICT.equals(rightExpression.getName())) return -1;

          final List<QualifiedRatedResolveResult> resolveResults =
            ((PyReferenceExpression)rightExpression).multiFollowAssignmentsChain(resolveContext);
          if (resolveResults.isEmpty()) {
            return -1;
          }

          final PsiElement pyElement = PyUtil.filterTopPriorityResults(resolveResults).get(0).getElement();
          if (pyElement == rightExpression || !(pyElement instanceof PyExpression)) {
            return -1;
          }
          if (pyElement instanceof PyDictLiteralExpression) {
            return inspectDict(rightExpression, problemTarget, true);
          }
          return inspectArguments((PyExpression)pyElement, problemTarget);
        }
        else if (rightExpression instanceof PyCallExpression) {
          final PyExpression callee = ((PyCallExpression)rightExpression).getCallee();
          if (callee != null && "dict".equals(callee.getName())) return 1;
          return inspectCallExpression((PyCallExpression)rightExpression, resolveContext, myTypeEvalContext);
        }
        else if (rightExpression instanceof PyParenthesizedExpression) {
          final PyExpression rhs = ((PyParenthesizedExpression)rightExpression).getContainedExpression();
          if (rhs != null) {
            return inspectArguments(rhs, problemTarget);
          }
        }
        else if (rightExpression instanceof PyTupleExpression) {
          if (PsiTreeUtil.isAncestor(problemTarget, rightExpression, false)) {
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
          else {
            final PyTupleType tupleType = (PyTupleType)myTypeEvalContext.getType(rightExpression);
            if (tupleType == null) {
              return -1;
            }
            matchEntireTupleTypes(problemTarget, tupleType);
            return tupleType.getElementCount();
          }
        }
        else if (rightExpression instanceof PyDictLiteralExpression) {
          return inspectDict(rightExpression, problemTarget, false);
        }
        else if (PsiTreeUtil.instanceOf(rightExpression, PySequenceExpression.class, PyComprehensionElement.class)) {
          if (s != null) {
            checkTypeCompatible(problemTarget, builtinCache.getStrType(),
                                PyTypeParser.getTypeByName(problemTarget, s, myTypeEvalContext));
            return 1;
          }
        }
        else if (rightExpression instanceof PySliceExpression && s != null) {
          final PyType type = myTypeEvalContext.getType(((PySliceExpression)rightExpression).getOperand());
          final PyType stringType = PyBuiltinCache.getInstance(rightExpression).getStringType(LanguageLevel.forElement(rightExpression));
          final PyType listType = PyBuiltinCache.getInstance(rightExpression).getListType();

          if (type == null) return -1;
          if (PyTypeChecker.match(listType, type, myTypeEvalContext)
              || PyTypeChecker.match(stringType, type, myTypeEvalContext)) {
            checkTypeCompatible(problemTarget, builtinCache.getStrType(),
                                PyTypeParser.getTypeByName(problemTarget, s, myTypeEvalContext));
            return 1;
          }
          PySliceItem sliceItem = ((PySliceExpression)rightExpression).getSliceItem();
          if (sliceItem != null) {
            PyExpression lower = sliceItem.getLowerBound();
            PyExpression upper = sliceItem.getUpperBound();
            PyExpression stride = sliceItem.getStride();
            if (upper instanceof PyNumericLiteralExpression) {
              BigInteger lowerVal;
              if (lower instanceof PyNumericLiteralExpression) {
                lowerVal = ((PyNumericLiteralExpression)lower).getBigIntegerValue();
              }
              else {
                lowerVal = BigInteger.ZERO;
              }
              int count = (((PyNumericLiteralExpression)upper).getBigIntegerValue().subtract(lowerVal)).intValue();
              int strideVal;
              if (stride instanceof PyNumericLiteralExpression) {
                strideVal = ((PyNumericLiteralExpression)stride).getBigIntegerValue().intValue();
              }
              else {
                strideVal = 1;
              }
              int res = count / strideVal;
              int residue = count % strideVal == 0 ? 0 : 1;
              return res + residue;
            }
          }
          return -1;
        }
        return -1;
      }

      private void matchEntireTupleTypes(@NotNull PsiElement rightExpression, PyTupleType rightExpressionType) {
        final List<PyType> expectedElementTypes = ContainerUtil.map(myFormatSpec.values(), name -> {
          if (name == null) {
            return null;
          }
          final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(rightExpression);
          final PyType expected = PyTypeParser.getTypeByName(rightExpression, name, myTypeEvalContext);
          if (expected == builtinCache.getStrType()) {
            return null;
          }
          return expected;
        });
        final PyTupleType expectedTupleType = PyTupleType.create(rightExpression, expectedElementTypes);
        checkTypeCompatible(rightExpression, rightExpressionType, expectedTupleType);
      }

      private static Map<PyExpression, PyExpression> addSubscriptions(PsiFile file, String operand) {
        Map<PyExpression, PyExpression> additionalExpressions = new HashMap<>();
        Collection<PySubscriptionExpression> subscriptionExpressions = PsiTreeUtil.findChildrenOfType(file, PySubscriptionExpression.class);
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
          final var resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);
          pyElement = ((PyReferenceExpression)rightExpression).followAssignmentsChain(resolveContext).getElement();
        }
        else {
          additionalExpressions = new HashMap<>();
          pyElement = rightExpression;
        }
        if (pyElement == null) return 0;
        final PyKeyValueExpression[] expressions = ((PyDictLiteralExpression)pyElement).getElements();
        if (myUsedMappingKeys.isEmpty()) {
          if (myExpectedArguments > 0) {
            if (myExpectedArguments > 1 && myExpectedArguments == (expressions.length + additionalExpressions.size())) {
              // probably "%s %s" % {'a':1, 'b':2}, with names forgotten in template
              registerProblem(rightExpression, PyPsiBundle.message("INSP.format.requires.no.mapping"));
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
        int referenceKeyNumber = 0;
        for (PyKeyValueExpression expression : expressions) {
          final PyExpression key = expression.getKey();
          final PyExpression value = expression.getValue();
          if (key instanceof PyStringLiteralExpression) {
            resolveMappingKey(problemTarget, (PyStringLiteralExpression)key, value);
          }
          else if (key instanceof PyReferenceExpression) {
            referenceKeyNumber++;
          }
        }
        for (Map.Entry<PyExpression, PyExpression> expression : additionalExpressions.entrySet()) {
          final PyExpression key = expression.getKey();
          final PyExpression value = expression.getValue();
          if (key instanceof PyStringLiteralExpression) {
            resolveMappingKey(problemTarget, (PyStringLiteralExpression)key, value);
          }
          else if (key instanceof PyReferenceExpression) {
            referenceKeyNumber++;
          }
        }

        int unresolved = 0;
        for (String key : myUsedMappingKeys.keySet()) {
          if (!myUsedMappingKeys.get(key).booleanValue()) {
            unresolved++;
            if (unresolved > referenceKeyNumber) {
              registerProblem(problemTarget, PyPsiBundle.message("INSP.str.format.key.has.no.argument", key));
              break;
            }
          }
        }
        return (expressions.length + additionalExpressions.size());
      }

      private void resolveMappingKey(PsiElement problemTarget, PyStringLiteralExpression key, PyExpression value) {
        final String name = key.getStringValue();
        if (myUsedMappingKeys.get(name) != null) {
          myUsedMappingKeys.put(name, true);
          if (value != null) {
            checkExpressionType(value, myFormatSpec.get(name), problemTarget);
          }
        }
      }

      private void registerProblem(@NotNull PsiElement problemTarget,
                                   @NotNull @InspectionMessage String message,
                                   @NotNull LocalQuickFix quickFix) {
        myProblemRegister = true;
        myVisitor.registerProblem(problemTarget, message, quickFix);
      }

      private void registerProblem(@NotNull PsiElement problemTarget, @NotNull @InspectionMessage String message) {
        myProblemRegister = true;
        myVisitor.registerProblem(problemTarget, message);
      }

      private void checkExpressionType(final @NotNull PyExpression expression,
                                       final @NotNull String expectedTypeName,
                                       @NotNull PsiElement problemTarget) {
        final PyType actual = myTypeEvalContext.getType(expression);
        final PyType expected = PyTypeParser.getTypeByName(problemTarget, expectedTypeName, myTypeEvalContext);
        if (actual != null) {
          checkTypeCompatible(problemTarget, actual, expected);
        }
      }

      private void checkTypeCompatible(final @NotNull PsiElement problemTarget,
                                       final @Nullable PyType actual,
                                       final @Nullable PyType expected) {
        if (expected != null && "str".equals(expected.getName())) {
          return;
        }
        if (actual != null && !PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
          registerProblem(problemTarget, PyPsiBundle.message("INSP.str.format.unexpected.argument.type", actual.getName()));
        }
      }

      private void inspectPercentFormat(final @NotNull PyStringLiteralExpression formatExpression) {
        final String value = formatExpression.getText();
        final List<SubstitutionChunk> chunks = filterSubstitutions(parsePercentFormat(value));

        myExpectedArguments = chunks.size();
        myUsedMappingKeys.clear();

        // if use mapping keys
        final boolean mapping = !chunks.isEmpty() && chunks.get(0).getMappingKey() != null;
        for (int i = 0; i < chunks.size(); ++i) {
          PercentSubstitutionChunk chunk = as(chunks.get(i), PercentSubstitutionChunk.class);
          if (chunk != null) {
            // Mapping key
            String mappingKey = Integer.toString(i + 1);
            if (mapping) {
              if (chunk.getMappingKey() == null || chunk.isUnclosedMapping()) {
                registerProblem(formatExpression, PyPsiBundle.message("INSP.too.few.keys"));
                break;
              }
              mappingKey = chunk.getMappingKey();
              myUsedMappingKeys.put(mappingKey, false);
            }

            // Minimum field width
            inspectWidth(formatExpression, chunk.getWidth());

            // Precision
            inspectWidth(formatExpression, chunk.getPrecision());

            // Format specifier
            final char conversionType = chunk.getConversionType();
            if (conversionType == 'b') {
              final LanguageLevel languageLevel = LanguageLevel.forElement(formatExpression);
              if (languageLevel.isOlderThan(LanguageLevel.PYTHON35) || !isBytesLiteral(formatExpression, myTypeEvalContext)) {
                registerProblem(formatExpression, PyPsiBundle.message("INSP.str.format.unsupported.format.character.b"));
                return;
              }
            }
            final LanguageLevel languageLevel = LanguageLevel.forElement(formatExpression);
            if (PERCENT_FORMAT_CONVERSIONS.containsKey(conversionType) && !(languageLevel.isPython2() && conversionType == 'a')) {
              myFormatSpec.put(mappingKey, PERCENT_FORMAT_CONVERSIONS.get(conversionType));
              continue;
            }
            registerProblem(formatExpression, PyPsiBundle.message("INSP.no.format.specifier.char"), new PyAddSpecifierToFormatQuickFix());
            return;
          }
        }
      }


      private static boolean isBytesLiteral(@NotNull PyStringLiteralExpression expr, @NotNull TypeEvalContext context) {
        final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(expr);
        final PyClassType bytesType = builtinCache.getBytesType(LanguageLevel.forElement(expr));
        final PyType actualType = context.getType(expr);
        return bytesType != null && actualType != null && PyTypeChecker.match(bytesType, actualType, context);
      }

      private void inspectWidth(final @NotNull PyStringLiteralExpression formatExpression, String width) {
        if ("*".equals(width)) {
          ++myExpectedArguments;
          if (!myUsedMappingKeys.isEmpty()) {
            registerProblem(formatExpression, PyPsiBundle.message("INSP.str.format.can.not.use.star.in.formats.when.using.mapping"));
          }
        }
      }

      public boolean isProblem() {
        return myProblemRegister;
      }

      private void inspectValues(final @Nullable PyExpression rightExpression) {
        if (rightExpression == null) {
          return;
        }
        if (rightExpression instanceof PyParenthesizedExpression) {
          inspectValues(((PyParenthesizedExpression)rightExpression).getContainedExpression());
        }
        else {
          final PyClassType type = as(myTypeEvalContext.getType(rightExpression), PyClassType.class);
          if (type != null) {
            if (!myUsedMappingKeys.isEmpty() && !PyABCUtil.isSubclass(type.getPyClass(), PyNames.MAPPING, myTypeEvalContext)) {
              registerProblem(rightExpression, PyPsiBundle.message("INSP.format.requires.mapping"));
              return;
            }
          }
          inspectArgumentsNumber(rightExpression);
        }
      }

      private void inspectArgumentsNumber(final @NotNull PyExpression rightExpression) {
        final int arguments = inspectArguments(rightExpression, rightExpression);
        if (myUsedMappingKeys.isEmpty() && arguments >= 0) {
          if (myExpectedArguments < arguments) {
            registerProblem(rightExpression, PyPsiBundle.message("INSP.too.many.args.for.fmt.string"));
          }
          else if (myExpectedArguments > arguments) {
            registerProblem(rightExpression, PyPsiBundle.message("INSP.too.few.args.for.fmt.string"));
          }
        }
      }
    }

    private static class NewStyleInspection {

      private static final List<String> CHECKED_TYPES =
        Arrays.asList(PyNames.TYPE_STR, PyNames.TYPE_INT, PyNames.TYPE_LONG, "float", "complex", "None", "LiteralString");

      private static final List<String> NUMERIC_TYPES = Arrays.asList(PyNames.TYPE_INT, PyNames.TYPE_LONG, "float", "complex");

      private static final ImmutableMap<Character, String> NEW_STYLE_FORMAT_CONVERSIONS = ImmutableMap.<Character, String>builder()
        .put('s', "str or None")
        .put('b', "int")
        .put('c', "int")
        .put('d', "int")
        .put('o', "int")
        .put('x', "int")
        .put('X', "int")
        .put('n', "int or long or float or complex")
        .put('e', "long or float or complex")
        .put('E', "long or float or complex")
        .put('f', "long or float or complex")
        .put('F', "long or float or complex")
        .put('g', "long or float or complex")
        .put('G', "long or float or complex")
        .put('%', "long or float")
        .build();

      private final PyStringLiteralExpression myFormatExpression;
      private boolean myProblemRegister = false;
      private final Visitor myVisitor;
      private final TypeEvalContext myTypeEvalContext;

      private final Map<String, String> myFormatSpec = new HashMap<>();

      NewStyleInspection(PyStringLiteralExpression formatExpression, Visitor visitor, TypeEvalContext context) {
        myFormatExpression = formatExpression;
        myVisitor = visitor;
        myTypeEvalContext = context;
      }

      public void inspect() {
        final String value = myFormatExpression.getText();
        PyStringFormatParser parser = new PyStringFormatParser(value);
        final List<NewStyleSubstitutionChunk> chunks = ContainerUtil.findAll(parser.parseNewStyle(), NewStyleSubstitutionChunk.class);

        for (int i = 0; i < chunks.size(); i++) {
          final NewStyleSubstitutionChunk prevChunk = i > 0 ? chunks.get(i - 1) : null;
          final NewStyleSubstitutionChunk chunk = chunks.get(i);

          if (prevChunk != null) {
            if (prevChunk.getManualPosition() != null && chunk.getAutoPosition() != null) {
              registerProblem(myFormatExpression, PyPsiBundle.message("INSP.manual.to.auto.field.numbering"));
            }
            else if (prevChunk.getAutoPosition() != null && chunk.getManualPosition() != null) {
              registerProblem(myFormatExpression, PyPsiBundle.message("INSP.auto.to.manual.field.numbering"));
            }
          }
          String mappingKey = inspectNewStyleChunkAndGetMappingKey(chunk);
          if (!isProblem()) {
            inspectArguments(chunk, mappingKey);
          }
        }
      }

      private String inspectNewStyleChunkAndGetMappingKey(@NotNull NewStyleSubstitutionChunk chunk) {
        final HashSet<String> supportedTypes = new HashSet<>();
        boolean hasTypeOptions = false;

        final String mappingKey = chunk.getMappingKey() != null ? chunk.getMappingKey() : String.valueOf(chunk.getPosition());

        // inspect options available only for numeric types
        if (chunk.hasSignOption() || chunk.useAlternateForm() || chunk.hasZeroPadding() || chunk.hasThousandsSeparator()) {
          specifyTypes(supportedTypes, NUMERIC_TYPES);
          hasTypeOptions = true;
        }

        if (chunk.getPrecision() != null) {
          // TODO: actually availableTypes doesn't reject int, because int is compatible with float and complex
          final List<String> availableTypes = Arrays.asList("str", "float", "complex");
          specifyTypes(supportedTypes, availableTypes);
          hasTypeOptions = true;
        }

        final char conversionType = chunk.getConversionType();
        if (conversionType != Character.MIN_VALUE) {
          if (NEW_STYLE_FORMAT_CONVERSIONS.containsKey(conversionType)) {
            final String[] s = NEW_STYLE_FORMAT_CONVERSIONS.get(conversionType).split(" or ");
            specifyTypes(supportedTypes, Arrays.asList(s));
            hasTypeOptions = true;
          }
          else {
            registerProblem(myFormatExpression, PyPsiBundle.message("INSP.unsupported.format.character", conversionType));
          }
        }

        if (!supportedTypes.isEmpty()) {
          myFormatSpec.put(mappingKey, StringUtil.join(supportedTypes, " or "));
        }
        else if (hasTypeOptions) {
          registerProblem(myFormatExpression, PyPsiBundle.message("INSP.incompatible.options", mappingKey));
        }
        return mappingKey;
      }

      private void inspectArguments(@NotNull NewStyleSubstitutionChunk chunk, @NotNull String mappingKey) {
        final PsiElement target = new PySubstitutionChunkReference(myFormatExpression, chunk).resolve();
        boolean hasElementIndex = chunk.getMappingKeyElementIndex() != null;
        if (target == null) {
          final String chunkMapping = chunk.getMappingKey();
          if (chunkMapping != null) {
            registerProblem(myFormatExpression, hasElementIndex ?
                                                PyPsiBundle.message("INSP.too.few.args.for.fmt.string") :
                                                PyPsiBundle.message("INSP.str.format.key.has.no.argument", chunkMapping));
          }
          else {
            registerProblem(myFormatExpression, PyPsiBundle.message("INSP.too.few.args.for.fmt.string"));
          }
        }
        else {
          checkTypesCompatibleForCheckedTypesOnly(myFormatExpression, target, mappingKey);
        }
      }

      private void registerProblem(@NotNull PsiElement problemTarget, @NotNull @InspectionMessage String message) {
        myProblemRegister = true;
        myVisitor.registerProblem(problemTarget, message);
      }

      private void checkTypesCompatibleForCheckedTypesOnly(@NotNull PyStringLiteralExpression anchor,
                                                           @NotNull PsiElement target,
                                                           @NotNull String mappingKey) {
        final PyTypedElement typedElement = as(target, PyTypedElement.class);
        if (typedElement != null && myFormatSpec.containsKey(mappingKey)) {
          final PyType actual = myTypeEvalContext.getType(typedElement);
          final PyType expected = PyTypeParser.getTypeByName(anchor, myFormatSpec.get(mappingKey));
          if (expected != null && actual != null
              && CHECKED_TYPES.contains(actual.getName())
              && !PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
            registerProblem(typedElement, PyPsiBundle.message("INSP.str.format.unexpected.argument.type", actual.getName()));
          }
        }
      }

      private static void specifyTypes(final @NotNull Set<String> types, final @NotNull List<String> supportedTypes) {
        if (types.isEmpty()) {
          types.addAll(supportedTypes);
        }
        else {
          types.retainAll(supportedTypes);
        }
      }

      public boolean isProblem() {
        return myProblemRegister;
      }
    }

    static int inspectCallExpression(@NotNull PyCallExpression callExpression,
                                     @NotNull PyResolveContext resolveContext,
                                     @NotNull TypeEvalContext evalContext) {
      final IntSummaryStatistics statistics = callExpression.multiResolveCalleeFunction(resolveContext)
        .stream()
        .map(callable -> callable.getCallType(evalContext, callExpression))
        .collect(
          Collectors.summarizingInt(
            callType -> {
              if (callType instanceof PyNoneType) {
                return 1;
              }
              else if (callType instanceof PyClassType) {
                return countElements(evalContext, (PyClassType)callType);
              }
              else if (callType instanceof PyUnionType) {
                int maxNumber = 1;
                boolean allForSure = true;
                for (PyType member : ((PyUnionType)callType).getMembers()) {
                  PyClassType classType = as(member, PyClassType.class);
                  if (classType != null) {
                    int elementsCount = countElements(evalContext, classType);
                    allForSure = allForSure && elementsCount != -1;
                    maxNumber = Math.max(maxNumber, elementsCount);
                  }
                  else if (!(member instanceof PyNoneType)) {
                    allForSure = false;
                  }
                }
                return allForSure ? maxNumber : -1;
              }

              return -1;
            }
          )
        );

      if (statistics.getMin() == statistics.getMax()) {
        return statistics.getMin();
      }
      else {
        return -1;
      }
    }

    private static int countElements(@NotNull TypeEvalContext evalContext, PyClassType callType) {
      if (!callType.getPyClass().isSubclass(PyNames.TUPLE, evalContext)) {
        return 1;
      }
      else if (callType instanceof PyTupleType) {
        return ((PyTupleType)callType).getElementCount();
      }
      return -1;
    }

    public Visitor(final ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyBinaryExpression(final @NotNull PyBinaryExpression node) {
      if (node.getLeftExpression() instanceof PyStringLiteralExpression literalExpression && node.isOperator("%")) {
        final Inspection inspection = new Inspection(this, myTypeEvalContext);
        inspection.inspectPercentFormat(literalExpression);
        if (inspection.isProblem()) {
          return;
        }
        inspection.inspectValues(node.getRightExpression());
      }
    }

    @Override
    public void visitPyCallExpression(@NotNull PyCallExpression node) {
      final PyExpression callee = node.getCallee();
      if (callee != null && callee.getName() != null && callee.getName().equals(PyNames.FORMAT)) {
        final PyStringLiteralExpression literalExpression = PsiTreeUtil.getChildOfType(callee, PyStringLiteralExpression.class);
        if (literalExpression != null) {
          final NewStyleInspection inspection = new NewStyleInspection(literalExpression, this, myTypeEvalContext);
          inspection.inspect();
        }
      }
    }
  }
}
