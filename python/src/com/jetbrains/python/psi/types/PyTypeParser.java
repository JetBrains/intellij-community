/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.psi.types;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.Function;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.functionalParser.ForwardDeclaration;
import com.jetbrains.python.psi.types.functionalParser.FunctionalParser;
import com.jetbrains.python.psi.types.functionalParser.ParserException;
import com.jetbrains.python.psi.types.functionalParser.Token;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

import static com.jetbrains.python.psi.types.PyTypeTokenTypes.IDENTIFIER;
import static com.jetbrains.python.psi.types.PyTypeTokenTypes.PARAMETER;
import static com.jetbrains.python.psi.types.functionalParser.FunctionalParserBase.*;

/**
 * @author vlan
 */
public class PyTypeParser {
  private static final ParseResult EMPTY_RESULT = new ParseResult(null, Collections.emptyMap(), Collections.emptyMap(),
                                                                  Collections.emptyMap());

  public static class ParseResult {
    @Nullable private final PyType myType;
    @NotNull private final Map<TextRange, ? extends PyType> myTypes;
    @NotNull private final Map<? extends PyType, TextRange> myFullRanges;
    @NotNull private final Map<? extends PyType, PyImportElement> myImports;

    ParseResult(@Nullable PyType type, @NotNull Map<TextRange, ? extends PyType> types,
                @NotNull Map<? extends PyType, TextRange> fullRanges,
                @NotNull Map<? extends PyType, PyImportElement> imports) {
      myType = type;
      myTypes = types;
      myFullRanges = fullRanges;
      myImports = imports;
    }

    ParseResult(@NotNull PyType type, @NotNull TextRange range) {
      this(type, ImmutableMap.of(range, type), ImmutableMap.of(type, range), ImmutableMap.of());
    }

    @Nullable
    public PyType getType() {
      return myType;
    }

    @NotNull
    public Map<TextRange, ? extends PyType> getTypes() {
      return myTypes;
    }

    @NotNull
    public Map<? extends PyType, TextRange> getFullRanges() {
      return myFullRanges;
    }

    @NotNull
    public Map<? extends PyType, PyImportElement> getImports() {
      return myImports;
    }

    private ParseResult merge(@NotNull ParseResult result) {
      final Map<TextRange, PyType> types = new HashMap<>();
      final Map<PyType, TextRange> fullRanges = new HashMap<>();
      final Map<PyType, PyImportElement> imports = new HashMap<>();
      types.putAll(myTypes);
      types.putAll(result.getTypes());
      fullRanges.putAll(myFullRanges);
      fullRanges.putAll(result.getFullRanges());
      imports.putAll(myImports);
      imports.putAll(result.getImports());
      return new ParseResult(myType, types, fullRanges, imports);
    }

    private ParseResult withType(@Nullable PyType type) {
      return new ParseResult(type, myTypes, myFullRanges, myImports);
    }
  }

  /**
   * @param anchor should never be null or null will be returned
   * @return null either if there was an error during parsing or if extracted type is equivalent to <tt>Any</tt> or <tt>undefined</tt>
   */
  @Nullable
  public static PyType getTypeByName(@Nullable PsiElement anchor, @NotNull String type) {
    if (anchor == null) return EMPTY_RESULT.getType();
    return parse(anchor, type).getType();
  }

  /**
   * @param anchor  should never be null or null will be returned
   * @param context type evaluation context
   * @return null either if there was an error during parsing or if extracted type is equivalent to <tt>Any</tt> or <tt>undefined</tt>
   */
  @Nullable
  public static PyType getTypeByName(@Nullable PsiElement anchor, @NotNull String type, @NotNull TypeEvalContext context) {
    if (anchor == null) return EMPTY_RESULT.getType();
    return parse(anchor, type, context).getType();
  }

  /**
   * @param anchor should never be null or {@link PyTypeParser#EMPTY_RESULT} will be returned
   * @param type   representation of the type to parse
   */
  @NotNull
  public static ParseResult parse(@NotNull PsiElement anchor, @NotNull String type) {
    return parse(anchor, type, TypeEvalContext.codeInsightFallback(anchor.getProject()));
  }

  /**
   * @param anchor  should never be null or {@link PyTypeParser#EMPTY_RESULT} will be returned
   * @param type    representation of the type to parse
   * @param context type evaluation context
   */
  @NotNull
  public static ParseResult parse(@NotNull PsiElement anchor, @NotNull String type, @NotNull TypeEvalContext context) {
    PyPsiUtils.assertValid(anchor);

    final ForwardDeclaration<ParseResult, PyElementType> typeExpr = ForwardDeclaration.create();

    final FunctionalParser<ParseResult, PyElementType> classType =
      token(IDENTIFIER).then(many(op(".").skipThen(token(IDENTIFIER))))
        .map(new MakeSimpleType(anchor, context))
        .cached()
        .named("class-type");

    final FunctionalParser<ParseResult, PyElementType> tupleType =
      op("(").skipThen(typeExpr).then(many(op(",").skipThen(typeExpr))).thenSkip(op(")"))
        .map(value -> {
          ParseResult result = value.getFirst();
          final List<ParseResult> rest = value.getSecond();
          if (rest.isEmpty()) {
            return result;
          }
          final List<PyType> types = new ArrayList<>();
          types.add(result.getType());
          for (ParseResult r : rest) {
            result = result.merge(r);
            types.add(r.getType());
          }
          return result.withType(PyTupleType.create(anchor, types));
        })
        .named("tuple-type");

    final FunctionalParser<ParseResult, PyElementType> typeParameter =
      token(PARAMETER).then(maybe(op("<=").skipThen(typeExpr)))
        .map(value -> {
          final Token<PyElementType> token = value.getFirst();
          final String name = token.getText().toString();
          final TextRange range = token.getRange();
          final ParseResult boundResult = value.getSecond();
          if (boundResult != null) {
            final PyGenericType type1 = new PyGenericType(name, boundResult.getType());
            final ParseResult result = new ParseResult(type1, range);
            return result.merge(boundResult).withType(type1);
          }
          return new ParseResult(new PyGenericType(name, null), range);
        })
        .named("type-parameter");

    final FunctionalParser<ParseResult, PyElementType> simpleExpr =
      classType
        .or(tupleType)
        .or(typeParameter)
        .named("simple-expr");

    final FunctionalParser<ParseResult, PyElementType> paramExpr =
      classType.thenSkip(op("[")).then(typeExpr).then(many(op(",").skipThen(typeExpr))).thenSkip(op("]"))
        .map(value -> {
          final Pair<ParseResult, ParseResult> firstPair = value.getFirst();
          final ParseResult first = firstPair.getFirst();
          final ParseResult second = firstPair.getSecond();
          final List<ParseResult> third = value.getSecond();
          final PyType firstType = first.getType();
          final List<PyType> typesInBrackets = new ArrayList<>();
          typesInBrackets.add(second.getType());
          ParseResult result = first;
          result = result.merge(second);
          for (ParseResult r : third) {
            typesInBrackets.add(r.getType());
            result = result.merge(r);
          }
          final List<PyType> elementTypes = third.isEmpty() ? Collections.singletonList(second.getType()) : typesInBrackets;
          if (firstType instanceof PyClassType) {
            final PyType type1 = new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false, elementTypes);
            return result.withType(type1);
          }
          return EMPTY_RESULT;
        })
        .or(classType.thenSkip(op("of")).then(simpleExpr)
              .map(value -> {
                final ParseResult firstResult = value.getFirst();
                final ParseResult secondResult = value.getSecond();
                final ParseResult result = firstResult.merge(secondResult);
                final PyType firstType = firstResult.getType();
                final PyType secondType = secondResult.getType();
                if (firstType != null) {
                  if (firstType instanceof PyClassType && secondType != null) {
                    return result.withType(new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false,
                                                                    Collections.singletonList(secondType)));
                  }
                  return result.withType(firstType);
                }
                return EMPTY_RESULT;
              }))
        .or(classType.thenSkip(op("from")).then(simpleExpr).thenSkip(op("to")).then(simpleExpr)
              .map(value -> {
                final Pair<ParseResult, ParseResult> firstPair = value.getFirst();
                final ParseResult first = firstPair.getFirst();
                final ParseResult second = firstPair.getSecond();
                final ParseResult third = value.getSecond();
                final PyType firstType = first.getType();
                if (firstType instanceof PyClassType) {
                  final List<PyType> elementTypes = Arrays.asList(second.getType(), third.getType());
                  final PyCollectionTypeImpl type1 = new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false,
                                                                              elementTypes);
                  return first.merge(second).merge(third).withType(type1);
                }
                return EMPTY_RESULT;
              }))
        .named("param-expr");

    final FunctionalParser<ParseResult, PyElementType> callableExpr =
      op("(").skipThen(maybe(typeExpr.then(many(op(",").skipThen(typeExpr))))).thenSkip(op(")")).thenSkip(op("->")).then(typeExpr)
        .map(
          value -> {
            final List<PyCallableParameter> parameters = new ArrayList<>();
            final ParseResult returnResult = value.getSecond();
            ParseResult result;
            final Pair<ParseResult, List<ParseResult>> firstPair = value.getFirst();
            if (firstPair != null) {
              final ParseResult first = firstPair.getFirst();
              final List<ParseResult> second = firstPair.getSecond();
              result = first;
              parameters.add(PyCallableParameterImpl.nonPsi(first.getType()));
              for (ParseResult r : second) {
                result = result.merge(r);
                parameters.add(PyCallableParameterImpl.nonPsi(r.getType()));
              }
              result = result.merge(returnResult);
            }
            else {
              result = returnResult;
            }
            return result.withType(new PyCallableTypeImpl(parameters, returnResult.getType()));
          })
        .named("callable-expr");

    final FunctionalParser<ParseResult, PyElementType> singleExpr =
      paramExpr
        .or(callableExpr)
        .or(simpleExpr)
        .named("single-expr");

    final FunctionalParser<ParseResult, PyElementType> unionExpr =
      singleExpr.then(many(op("or").or(op("|")).skipThen(singleExpr)))
        .map(value -> {
          final ParseResult first = value.getFirst();
          final List<ParseResult> rest = value.getSecond();
          if (rest.isEmpty()) {
            return first;
          }
          final List<PyType> types = new ArrayList<>();
          types.add(first.getType());
          ParseResult result = first;
          for (ParseResult r : rest) {
            types.add(r.getType());
            result = result.merge(r);
          }
          return result.withType(PyUnionType.union(types));
        })
        .named("union-expr");

    typeExpr
      .define(unionExpr)
      .named("type-expr");

    final FunctionalParser<ParseResult, PyElementType> typeFile =
      typeExpr
        .endOfInput()
        .named("type-file");

    try {
      return typeFile.parse(tokenize(type));
    }
    catch (ParserException e) {
      return EMPTY_RESULT;
    }
  }

  private static class MakeSimpleType implements Function<Pair<Token<PyElementType>, List<Token<PyElementType>>>, ParseResult> {
    @NotNull private final PsiElement myAnchor;
    @NotNull private final TypeEvalContext myContext;

    public MakeSimpleType(@NotNull PsiElement anchor, @NotNull TypeEvalContext context) {
      myAnchor = anchor;
      myContext = context;
    }

    @Nullable
    @Override
    public ParseResult fun(@NotNull Pair<Token<PyElementType>, List<Token<PyElementType>>> value) {
      final Token<PyElementType> first = value.getFirst();
      final List<Token<PyElementType>> rest = value.getSecond();
      final TextRange firstRange = first.getRange();
      final boolean unqualified = rest.isEmpty();

      if (unqualified) {
        final ParseResult result = parseBuiltinType(first);
        if (result != null) {
          return result;
        }
      }

      final PsiFile file = myAnchor.getContainingFile();
      final List<Token<PyElementType>> tokens = new ArrayList<>();
      tokens.add(first);
      tokens.addAll(rest);

      if (file instanceof PyFile) {
        final PyFile pyFile = (PyFile)file;
        final Map<TextRange, PyType> types = new HashMap<>();
        final Map<PyType, TextRange> fullRanges = new HashMap<>();
        final Map<PyType, PyImportElement> imports = new HashMap<>();

        PyType type = resolveQualifierType(tokens, pyFile, myContext, types, fullRanges, imports);

        if (type != null) {
          final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(myContext);
          final PyExpression expression = myAnchor instanceof PyExpression ? (PyExpression)myAnchor : null;

          for (Token<PyElementType> token : tokens) {
            final PyType qualifierType = type;
            type = null;
            final List<? extends RatedResolveResult> results = qualifierType.resolveMember(token.getText().toString(), expression,
                                                                                           AccessDirection.READ, resolveContext);
            if (results != null && !results.isEmpty()) {
              final PsiElement resolved = results.get(0).getElement();
              if (resolved instanceof PyTypedElement) {
                type = myContext.getType((PyTypedElement)resolved);
                if (type != null && !allowResolveToType(type)) {
                  type = null;
                  break;
                }
                if (type instanceof PyClassLikeType) {
                  type = ((PyClassLikeType)type).toInstance();
                }
              }
            }
            if (type == null) {
              break;
            }
            types.put(token.getRange(), type);
            fullRanges.put(type, TextRange.create(firstRange.getStartOffset(), token.getRange().getEndOffset()));
          }
          if (type != null) {
            return new ParseResult(type, types, fullRanges, imports);
          }
        }
      }

      return EMPTY_RESULT;
    }

    @Nullable
    private PyType resolveQualifierType(@NotNull List<Token<PyElementType>> tokens,
                                        @NotNull PyFile file,
                                        @NotNull TypeEvalContext context,
                                        @NotNull Map<TextRange, PyType> types,
                                        @NotNull Map<PyType, TextRange> fullRanges,
                                        @NotNull Map<PyType, PyImportElement> imports) {
      if (tokens.isEmpty()) {
        return null;
      }
      final Token<PyElementType> firstToken = tokens.get(0);
      final String firstText = firstToken.getText().toString();
      final TextRange firstRange = firstToken.getRange();
      final List<RatedResolveResult> resolveResults = file.multiResolveName(firstText);
      if (resolveResults.isEmpty()) {
        return getImplicitlyResolvedType(tokens, context, types, fullRanges, firstRange);
      }
      final List<PyType> members = Lists.newArrayList();
      for (RatedResolveResult result : resolveResults) {
        final PsiElement resolved = result.getElement();
        PyType type = null;
        if (resolved instanceof PyTypedElement) {
          type = context.getType((PyTypedElement)resolved);
        }
        if (type != null) {
          if (!allowResolveToType(type)) {
            continue;
          }
          if (type instanceof PyClassLikeType) {
            type = ((PyClassLikeType)type).toInstance();
          }
          types.put(firstRange, type);
          fullRanges.put(type, firstRange);
          for (PyFromImportStatement fromImportStatement : file.getFromImports()) {
            for (PyImportElement importElement : fromImportStatement.getImportElements()) {
              if (firstText.equals(importElement.getVisibleName())) {
                imports.put(type, importElement);
              }
            }
          }
          for (PyImportElement importElement : file.getImportTargets()) {
            if (firstText.equals(importElement.getVisibleName())) {
              imports.put(type, importElement);
            }
          }
        }
        members.add(type);
      }
      if (!members.isEmpty()) {
        tokens.remove(0);
      }
      return PyUnionType.union(members);
    }

    @Nullable
    private PyType getImplicitlyResolvedType(@NotNull List<Token<PyElementType>> tokens,
                                             @NotNull TypeEvalContext context,
                                             @NotNull Map<TextRange, PyType> types,
                                             @NotNull Map<PyType, TextRange> fullRanges,
                                             TextRange firstRange) {
      PyType type = null;
      QualifiedName qName = null;
      while (!tokens.isEmpty()) {
        final Token<PyElementType> token = tokens.get(0);
        final String name = token.getText().toString();
        qName = qName != null ? qName.append(name) : QualifiedName.fromComponents(name);
        final List<PsiElement> modules = PyResolveImportUtil.resolveQualifiedName(qName, PyResolveImportUtil.fromFoothold(myAnchor));
        PsiElement module = !modules.isEmpty() ? modules.get(0) : null;
        if (module == null) {
          break;
        }
        if (module instanceof PsiDirectory) {
          module = PyUtil.getPackageElement((PsiDirectory)module, myAnchor);
        }
        if (module instanceof PyTypedElement) {
          final PyType moduleType = context.getType((PyTypedElement)module);
          if (moduleType != null) {
            type = moduleType;
            types.put(token.getRange(), type);
            fullRanges.put(type, TextRange.create(firstRange.getStartOffset(), token.getRange().getEndOffset()));
          }
        }
        tokens.remove(0);
      }
      return type;
    }

    private static boolean allowResolveToType(@NotNull PyType type) {
      return type instanceof PyClassLikeType || type instanceof PyModuleType || type instanceof PyImportedModuleType ||
             type instanceof PyGenericType;
    }

    @Nullable
    private ParseResult parseBuiltinType(@NotNull Token<PyElementType> token) {
      final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(myAnchor);
      final String name = token.getText().toString();
      final TextRange range = token.getRange();

      if (PyNames.UNKNOWN_TYPE.equals(name)) {
        return EMPTY_RESULT;
      }
      else if (PyNames.NONE.equals(name)) {
        return new ParseResult(PyNoneType.INSTANCE, range);
      }
      else if ("integer".equals(name) || PyNames.TYPE_LONG.equals(name)) {
        final PyClassType type = builtinCache.getIntType();
        return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
      }
      else if ("string".equals(name)) {
        final PyType type = builtinCache.getStringType(LanguageLevel.forElement(myAnchor));
        return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
      }
      else if ("bytestring".equals(name)) {
        final PyType type = builtinCache.getByteStringType(LanguageLevel.forElement(myAnchor));
        return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
      }
      else if ("bytes".equals(name)) {
        final PyClassType type = builtinCache.getBytesType(LanguageLevel.forElement(myAnchor));
        return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
      }
      else if (PyNames.TYPE_UNICODE.equals(name)) {
        final PyClassType type = builtinCache.getUnicodeType(LanguageLevel.forElement(myAnchor));
        return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
      }
      else if ("boolean".equals(name)) {
        final PyClassType type = builtinCache.getBoolType();
        return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
      }
      else if ("dictionary".equals(name)) {
        final PyClassType type = builtinCache.getDictType();
        return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
      }
      else if (PyNames.BASESTRING.equals(name)) {
        final PyType type = builtinCache.getStrOrUnicodeType();
        return type != null ? new ParseResult(type, range) : EMPTY_RESULT;
      }

      final PyType builtinType = builtinCache.getObjectType(name);
      if (builtinType != null) {
        return new ParseResult(builtinType, range);
      }

      return null;
    }
  }

  private static FunctionalParser<Token<PyElementType>, PyElementType> op(@Nullable String text) {
    return token(PyTypeTokenTypes.OP, text);
  }

  private static List<Token<PyElementType>> tokenize(@NotNull String s) {
    final List<Token<PyElementType>> tokens = new ArrayList<>();
    final _PyTypeLexer lexer = new _PyTypeLexer(new StringReader(s));
    lexer.reset(s, 0, s.length(), lexer.yystate());
    try {
      PyElementType type;
      while ((type = lexer.advance()) != null) {
        if (type == PyTypeTokenTypes.SPACE || type == PyTypeTokenTypes.MARKUP) {
          continue;
        }
        final TextRange range = TextRange.create(lexer.getTokenStart(), lexer.getTokenEnd());
        final Token<PyElementType> token = new Token<>(type, lexer.yytext(), range);
        tokens.add(token);
      }
    }
    catch (IOException | Error e) {
      return Collections.emptyList();
    }
    return tokens;
  }
}
