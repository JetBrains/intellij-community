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
package com.jetbrains.python.psi.types;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameResolverImpl;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
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
  private static final ParseResult EMPTY_RESULT = new ParseResult(null, Collections.<TextRange, PyType>emptyMap(), Collections.<PyType, TextRange>emptyMap(),
                                                                  Collections.<PyType, PyImportElement>emptyMap());

  public static class ParseResult {
    @Nullable private PyType myType;
    @NotNull private Map<TextRange, ? extends PyType> myTypes;
    @NotNull private Map<? extends PyType, TextRange> myFullRanges;
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
      this(type, ImmutableMap.of(range, type), ImmutableMap.of(type, range), ImmutableMap.<PyType, PyImportElement>of());
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
      final Map<TextRange, PyType> types = new HashMap<TextRange, PyType>();
      final Map<PyType, TextRange> fullRanges = new HashMap<PyType, TextRange>();
      final Map<PyType, PyImportElement> imports = new HashMap<PyType, PyImportElement>();
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

  @Nullable
  public static PyType getTypeByName(@Nullable final PsiElement anchor, @NotNull String type) {
    return parse(anchor, type).getType();
  }

  @NotNull
  public static ParseResult parse(@Nullable final PsiElement anchor, @NotNull String type) {
    if (anchor == null || !anchor.isValid()) {
      return EMPTY_RESULT;
    }

    final ForwardDeclaration<ParseResult, PyElementType> typeExpr = ForwardDeclaration.create();

    final FunctionalParser<ParseResult, PyElementType> classType =
      token(IDENTIFIER).then(many(op(".").skipThen(token(IDENTIFIER))))
        .map(new MakeSimpleType(anchor))
        .cached()
        .named("class-type");

    final FunctionalParser<ParseResult, PyElementType> tupleType =
      op("(").skipThen(typeExpr).then(many(op(",").skipThen(typeExpr))).thenSkip(op(")"))
        .map(new Function<Pair<ParseResult, List<ParseResult>>, ParseResult>() {
          @Override
          public ParseResult fun(Pair<ParseResult, List<ParseResult>> value) {
            ParseResult result = value.getFirst();
            final List<ParseResult> rest = value.getSecond();
            if (rest.isEmpty()) {
              return result;
            }
            final List<PyType> types = new ArrayList<PyType>();
            types.add(result.getType());
            for (ParseResult r : rest) {
              result = result.merge(r);
              types.add(r.getType());
            }
            return result.withType(PyTupleType.create(anchor, types.toArray(new PyType[types.size()])));
          }
        })
        .named("tuple-type");

    final FunctionalParser<ParseResult, PyElementType> typeParameter =
      token(PARAMETER).then(maybe(op("<=").skipThen(typeExpr)))
        .map(new Function<Pair<Token<PyElementType>, ParseResult>, ParseResult>() {
          @Override
          public ParseResult fun(Pair<Token<PyElementType>, ParseResult> value) {
            final Token<PyElementType> token = value.getFirst();
            final String name = token.getText().toString();
            final TextRange range = token.getRange();
            final ParseResult boundResult = value.getSecond();
            if (boundResult != null) {
              final PyGenericType type = new PyGenericType(name, boundResult.getType());
              final ParseResult result = new ParseResult(type, range);
              return result.merge(boundResult).withType(type);
            }
            return new ParseResult(new PyGenericType(name, null), range);
          }
        })
        .named("type-parameter");

    final FunctionalParser<ParseResult, PyElementType> simpleExpr =
      classType
        .or(tupleType)
        .or(typeParameter)
        .named("simple-expr");

    final FunctionalParser<ParseResult, PyElementType> paramExpr =
      classType.thenSkip(op("[")).then(typeExpr).then(many(op(",").skipThen(typeExpr))).thenSkip(op("]"))
        .map(new Function<Pair<Pair<ParseResult, ParseResult>, List<ParseResult>>, ParseResult>() {
          @Override
          public ParseResult fun(Pair<Pair<ParseResult, ParseResult>, List<ParseResult>> value) {
            final Pair<ParseResult, ParseResult> firstPair = value.getFirst();
            final ParseResult first = firstPair.getFirst();
            final ParseResult second = firstPair.getSecond();
            final List<ParseResult> third = value.getSecond();
            final PyType firstType = first.getType();
            if (firstType instanceof PyClassType) {
              final List<PyType> tupleTypes = new ArrayList<PyType>();
              tupleTypes.add(second.getType());
              ParseResult result = first;
              result = result.merge(second);
              for (ParseResult r : third) {
                tupleTypes.add(r.getType());
                result = result.merge(r);
              }
              final PyType elementType = third.isEmpty() ? second.getType() :
                                         PyTupleType.create(anchor, tupleTypes.toArray(new PyType[tupleTypes.size()]));
              final PyCollectionTypeImpl type = new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false, elementType);
              return result.withType(type);
            }
            return EMPTY_RESULT;
          }
        })
        .or(classType.thenSkip(op("of")).then(simpleExpr)
              .map(new Function<Pair<ParseResult, ParseResult>, ParseResult>() {
                @Override
                public ParseResult fun(Pair<ParseResult, ParseResult> value) {
                  final ParseResult firstResult = value.getFirst();
                  final ParseResult secondResult = value.getSecond();
                  final ParseResult result = firstResult.merge(secondResult);
                  final PyType firstType = firstResult.getType();
                  final PyType secondType = secondResult.getType();
                  if (firstType != null) {
                    if (firstType instanceof PyClassType && secondType != null) {
                      return result.withType(new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false, secondType));
                    }
                    return result.withType(firstType);
                  }
                  return EMPTY_RESULT;
                }
              }))
        .or(classType.thenSkip(op("from")).then(simpleExpr).thenSkip(op("to")).then(simpleExpr)
              .map(new Function<Pair<Pair<ParseResult, ParseResult>, ParseResult>, ParseResult>() {
                @Override
                public ParseResult fun(Pair<Pair<ParseResult, ParseResult>, ParseResult> value) {
                  final Pair<ParseResult, ParseResult> firstPair = value.getFirst();
                  final ParseResult first = firstPair.getFirst();
                  final ParseResult second = firstPair.getSecond();
                  final ParseResult third = value.getSecond();
                  final PyType firstType = first.getType();
                  if (firstType instanceof PyClassType) {
                    final PyTupleType tupleType = PyTupleType.create(anchor, new PyType[]{second.getType(), third.getType()});
                    final PyCollectionTypeImpl type = new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false, tupleType);
                    return first.merge(second).merge(third).withType(type);
                  }
                  return EMPTY_RESULT;
                }
              }))
        .named("param-expr");

    final FunctionalParser<ParseResult, PyElementType> callableExpr =
      op("(").skipThen(maybe(typeExpr.then(many(op(",").skipThen(typeExpr))))).thenSkip(op(")")).thenSkip(op("->")).then(typeExpr)
        .map(
          new Function<Pair<Pair<ParseResult, List<ParseResult>>, ParseResult>, ParseResult>() {
            @Override
            public ParseResult fun(Pair<Pair<ParseResult, List<ParseResult>>, ParseResult> value) {
              final List<PyCallableParameter> parameters = new ArrayList<PyCallableParameter>();
              final ParseResult returnResult = value.getSecond();
              ParseResult result;
              final Pair<ParseResult, List<ParseResult>> firstPair = value.getFirst();
              if (firstPair != null) {
                final ParseResult first = firstPair.getFirst();
                final List<ParseResult> second = firstPair.getSecond();
                result = first;
                parameters.add(new PyCallableParameterImpl(null, first.getType()));
                for (ParseResult r : second) {
                  result = result.merge(r);
                  parameters.add(new PyCallableParameterImpl(null, r.getType()));
                }
                result = result.merge(returnResult);
              }
              else {
                result = returnResult;
              }
              return result.withType(new PyCallableTypeImpl(parameters, returnResult.getType()));
            }
          })
        .named("callable-expr");

    final FunctionalParser<ParseResult, PyElementType> singleExpr =
      paramExpr
        .or(callableExpr)
        .or(simpleExpr)
        .named("single-expr");

    final FunctionalParser<ParseResult, PyElementType> unionExpr =
      singleExpr.then(many(op("or").or(op("|")).skipThen(singleExpr)))
        .map(new Function<Pair<ParseResult, List<ParseResult>>, ParseResult>() {
          @Override
          public ParseResult fun(Pair<ParseResult, List<ParseResult>> value) {
            final ParseResult first = value.getFirst();
            final List<ParseResult> rest = value.getSecond();
            if (rest.isEmpty()) {
              return first;
            }
            final List<PyType> types = new ArrayList<PyType>();
            types.add(first.getType());
            ParseResult result = first;
            for (ParseResult r : rest) {
              types.add(r.getType());
              result = result.merge(r);
            }
            return result.withType(PyUnionType.union(types));
          }
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

    public MakeSimpleType(@NotNull PsiElement anchor) {
      myAnchor = anchor;
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
      final List<Token<PyElementType>> tokens = new ArrayList<Token<PyElementType>>();
      tokens.add(first);
      tokens.addAll(rest);

      if (file instanceof PyFile) {
        final PyFile pyFile = (PyFile)file;
        final TypeEvalContext context = TypeEvalContext.codeInsightFallback();
        final Map<TextRange, PyType> types = new HashMap<TextRange, PyType>();
        final Map<PyType, TextRange> fullRanges = new HashMap<PyType, TextRange>();
        final Map<PyType, PyImportElement> imports = new HashMap<PyType, PyImportElement>();

        PyType type = resolveQualifierType(tokens, pyFile, context, types, fullRanges, imports);

        if (type != null) {
          final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
          final PyExpression expression = myAnchor instanceof PyExpression ? (PyExpression)myAnchor : null;

          for (Token<PyElementType> token : tokens) {
            final PyType qualifierType = type;
            type = null;
            final List<? extends RatedResolveResult> results = qualifierType.resolveMember(token.getText().toString(), expression,
                                                                                           AccessDirection.READ, resolveContext);
            if (results != null && !results.isEmpty()) {
              final PsiElement resolved = results.get(0).getElement();
              if (resolved instanceof PyTypedElement) {
                type = context.getType((PyTypedElement)resolved);
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

      if (unqualified) {
        final ParseResult result = fromClassNameIndex(first);
        if (result != null) {
          return result;
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
      PyType type = null;
      PsiElement resolved = file.getElementNamed(firstText);
      if (resolved != null) {
        // Local or imported name
        if (resolved instanceof PyTypedElement) {
          type = context.getType((PyTypedElement)resolved);
          if (type != null) {
            tokens.remove(0);
            if (!allowResolveToType(type)) {
              return null;
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
        }
      }
      else {
        // Implicitly available in the type string
        QualifiedName qName = null;
        while (!tokens.isEmpty()) {
          final Token<PyElementType> token = tokens.get(0);
          final String name = token.getText().toString();
          qName = qName != null ? qName.append(name) : QualifiedName.fromComponents(name);
          PsiElement module = new QualifiedNameResolverImpl(qName).fromElement(myAnchor).firstResult();
          if (module == null) {
            break;
          }
          if (module instanceof PsiDirectory) {
            module = PyUtil.getPackageElement((PsiDirectory)module);
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
      }
      return type;
    }

    private static boolean allowResolveToType(@NotNull PyType type) {
      return type instanceof PyClassLikeType || type instanceof PyModuleType || type instanceof PyImportedModuleType;
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
      else if ("integer".equals(name) || ("long".equals(name) && LanguageLevel.forElement(myAnchor).isPy3K())) {
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
      else if ("unicode".equals(name)) {
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

      final PyType builtinType = builtinCache.getObjectType(name);
      if (builtinType != null) {
        return new ParseResult(builtinType, range);
      }

      return null;
    }

    @Nullable
    private ParseResult fromClassNameIndex(@NotNull Token<PyElementType> token) {
      final Collection<PyClass> classes = PyClassNameIndex.find(token.getText().toString(), myAnchor.getProject(), true);
      if (classes.size() == 1) {
        return parseResultFromClass(token, classes.iterator().next());
      }
      for (PyClass cls : classes) {
        final PsiFile file = cls.getContainingFile();
        if (file != null && !PyUserSkeletonsUtil.isUnderUserSkeletonsDirectory(file)) {
          return parseResultFromClass(token, cls);
        }
      }
      return null;
    }

    @NotNull
    private static ParseResult parseResultFromClass(@NotNull Token<PyElementType> token, @NotNull PyClass cls) {
      final PyClassTypeImpl type = new PyClassTypeImpl(cls, false);
      type.assertValid("PyClassNameIndex.find().iterator()");
      return new ParseResult(type, token.getRange());
    }
  }

  private static FunctionalParser<Token<PyElementType>, PyElementType> op(@Nullable String text) {
    return token(PyTypeTokenTypes.OP, text);
  }

  private static List<Token<PyElementType>> tokenize(@NotNull String s) {
    final List<Token<PyElementType>> tokens = new ArrayList<Token<PyElementType>>();
    final PyTypeLexer lexer = new PyTypeLexer(new StringReader(s));
    lexer.reset(s, 0, s.length(), lexer.yystate());
    try {
      PyElementType type;
      while ((type = lexer.advance()) != null) {
        if (type == PyTypeTokenTypes.SPACE || type == PyTypeTokenTypes.MARKUP) {
          continue;
        }
        final TextRange range = TextRange.create(lexer.getTokenStart(), lexer.getTokenEnd());
        final Token<PyElementType> token = new Token<PyElementType>(type, lexer.yytext(), range);
        tokens.add(token);
      }
    }
    catch (IOException e) {
      return Collections.emptyList();
    }
    catch (Error e) {
      return Collections.emptyList();
    }
    return tokens;
  }
}
