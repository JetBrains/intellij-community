package com.jetbrains.python.psi.types;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
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
import static com.jetbrains.python.psi.types.functionalParser.FunctionalParserBase.many;
import static com.jetbrains.python.psi.types.functionalParser.FunctionalParserBase.token;

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

    ParseResult(@Nullable PyType type, @NotNull TextRange range) {
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

    final FunctionalParser<ParseResult, PyElementType> simpleType =
      token(IDENTIFIER).then(many(op(".").skipThen(token(IDENTIFIER))))
        .map(new MakeSimpleType(anchor))
        .named("simple-type");

    final FunctionalParser<ParseResult, PyElementType> tupleType =
      op("(").skipThen(typeExpr).then(many(op(",").skipThen(typeExpr))).thenSkip(op(")"))
        .map(new Function<Pair<ParseResult, List<ParseResult>>, ParseResult>() {
          @Override
          public ParseResult fun(Pair<ParseResult, List<ParseResult>> value) {
            final List<PyType> types = new ArrayList<PyType>();
            ParseResult result = value.getFirst();
            types.add(result.getType());
            for (ParseResult r : value.getSecond()) {
              result = result.merge(r);
              types.add(r.getType());
            }
            return result.withType(PyTupleType.create(anchor, types.toArray(new PyType[types.size()])));
          }
        })
        .named("tuple-type");

    final FunctionalParser<ParseResult, PyElementType> typeParameter =
      token(PARAMETER)
        .map(new Function<Token<PyElementType>, ParseResult>() {
          @Override
          public ParseResult fun(Token<PyElementType> token) {
            return new ParseResult(new PyGenericType(token.getText().toString()), token.getRange());
          }
        })
        .named("type-parameter");

    final FunctionalParser<ParseResult, PyElementType> simpleExpr =
      simpleType
        .or(tupleType)
        .or(typeParameter)
        .cached()
        .named("simple-expr");

    final FunctionalParser<ParseResult, PyElementType> paramExpr =
      simpleExpr.thenSkip(op("of")).then(simpleExpr)
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
        })
        .or(simpleExpr.thenSkip(op("from")).then(simpleExpr).thenSkip(op("to")).then(simpleExpr)
              .map(new Function<Pair<Pair<ParseResult, ParseResult>, ParseResult>, ParseResult>() {
                @Override
                public ParseResult fun(Pair<Pair<ParseResult, ParseResult>, ParseResult> value) {
                  final Pair<ParseResult, ParseResult> firstPair = value.getFirst();
                  final ParseResult first = firstPair.getFirst();
                  final ParseResult second = firstPair.getSecond();
                  final ParseResult third = value.getSecond();
                  final PyType firstType = first.getType();
                  if (firstType instanceof PyClassType) {
                    final PyTupleType tupleType = PyTupleType.create(anchor, new PyType[] { second.getType(), third.getType() });
                    final PyCollectionTypeImpl type = new PyCollectionTypeImpl(((PyClassType)firstType).getPyClass(), false, tupleType);
                    return first.merge(second).merge(third).withType(type);
                  }
                  return EMPTY_RESULT;
                }
              }))
        .or(simpleExpr)
        .named("param-expr");

    final FunctionalParser<ParseResult, PyElementType> unionExpr =
      paramExpr.then(many(op("or").skipThen(paramExpr)))
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
      final String firstText = first.getText().toString();

      if (rest.isEmpty()) {
        final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(myAnchor);

        if ("unknown".equals(firstText)) {
          return EMPTY_RESULT;
        }
        else if (PyNames.NONE.equals(firstText)) {
          return new ParseResult(PyNoneType.INSTANCE, firstRange);
        }
        else if ("integer".equals(firstText) || ("long".equals(firstText) && LanguageLevel.forElement(myAnchor).isPy3K())) {
          return new ParseResult(builtinCache.getIntType(), firstRange);
        }
        else if ("string".equals(firstText)) {
          return new ParseResult(builtinCache.getStringType(LanguageLevel.forElement(myAnchor)), firstRange);
        }
        else if ("bytes".equals(firstText)) {
          return new ParseResult(builtinCache.getBytesType(LanguageLevel.forElement(myAnchor)), firstRange);
        }
        else if ("unicode".equals(firstText)) {
          return new ParseResult(builtinCache.getUnicodeType(LanguageLevel.forElement(myAnchor)), firstRange);
        }
        else if ("boolean".equals(firstText)) {
          return new ParseResult(builtinCache.getBoolType(), firstRange);
        }
        else if ("dictionary".equals(firstText)) {
          return new ParseResult(builtinCache.getDictType(), firstRange);
        }

        final PyType builtinType = builtinCache.getObjectType(firstText);
        if (builtinType != null) {
          return new ParseResult(builtinType, firstRange);
        }
      }

      final PsiFile file = myAnchor.getContainingFile();

      if (file instanceof PyFile) {
        final PyFile pyFile = (PyFile)file;

        PsiElement resolved = pyFile.getElementNamed(firstText);
        if (resolved == null) {
          resolved = new QualifiedNameResolverImpl(firstText).fromElement(myAnchor).firstResult();
        }

        if (resolved instanceof PyTypedElement) {
          final Map<TextRange, PyType> types = new HashMap<TextRange, PyType>();
          final Map<PyType, TextRange> fullRanges = new HashMap<PyType, TextRange>();
          final Map<PyType, PyImportElement> imports = new HashMap<PyType, PyImportElement>();
          final TypeEvalContext context = TypeEvalContext.codeInsightFallback();
          PyType type = context.getType((PyTypedElement)resolved);
          if (type != null) {
            if (type instanceof PyClassLikeType) {
              type = ((PyClassLikeType)type).toInstance();
            }
            types.put(firstRange, type);
            fullRanges.put(type, firstRange);
            for (PyFromImportStatement fromImportStatement : pyFile.getFromImports()) {
              for (PyImportElement importElement : fromImportStatement.getImportElements()) {
                if (firstText.equals(importElement.getVisibleName())) {
                  imports.put(type, importElement);
                }
              }
            }

            final PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
            final PyExpression expression = myAnchor instanceof PyExpression ? (PyExpression)myAnchor : null;

            for (Token<PyElementType> token : rest) {
              final List<? extends RatedResolveResult> results =
                type.resolveMember(token.getText().toString(), expression, AccessDirection.READ, resolveContext);
              if (results != null && !results.isEmpty()) {
                resolved = results.get(0).getElement();
                if (resolved instanceof PyTypedElement) {
                  type = context.getType((PyTypedElement)resolved);
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
      }

      if (rest.isEmpty()) {
        final Collection<PyClass> classes = PyClassNameIndex.find(firstText, myAnchor.getProject(), true);
        if (classes.size() == 1) {
          final PyClassTypeImpl type = new PyClassTypeImpl(classes.iterator().next(), false);
          type.assertValid("PyClassNameIndex.find().iterator().next()");
          return new ParseResult(type, firstRange);
        }
      }

      return EMPTY_RESULT;
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
