/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.types.functionalParser;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.reference.SoftReference.dereference;

public abstract class FunctionalParserBase<R, T> implements FunctionalParser<R, T> {
  private @Nullable String myName = null;

  @Override
  public String toString() {
    return myName != null ? String.format("<%s>", myName) : super.toString();
  }

  @Override
  public @NotNull R parse(@NotNull List<Token<T>> tokens) throws ParserException {
    return parse(tokens, new State()).getFirst();
  }

  public static @NotNull <T> FunctionalParser<Token<T>, T> token(@NotNull T type) {
    return token(type, null);
  }

  public static @NotNull <T> FunctionalParser<Token<T>, T> token(final @NotNull T type, final @Nullable String text) {
    return new TokenParser<>(type, text);
  }

  public static @NotNull <R, T> FunctionalParser<List<R>, T> many(final @NotNull FunctionalParser<R, T> parser) {
    return new ManyParser<>(parser);
  }

  public static @NotNull <R, T> FunctionalParser<R, T> maybe(final @NotNull FunctionalParser<R, T> parser) {
    return parser.or(FunctionalParserBase.pure(null));
  }

  @Override
  public @NotNull FunctionalParser<R, T> endOfInput() {
    return this.thenSkip(FunctionalParserBase.finished());
  }

  @Override
  public @NotNull FunctionalParser<R, T> named(@NotNull String name) {
    myName = name;
    return this;
  }

  @Override
  public @NotNull FunctionalParser<R, T> cached() {
    return new CachedParser<>(this);
  }

  @Override
  public @NotNull <R2> FunctionalParser<Pair<R, R2>, T> then(final @NotNull FunctionalParser<R2, T> parser) {
    return new ThenParser<>(this, parser);
  }

  @Override
  public @NotNull <R2> FunctionalParser<R2, T> skipThen(final @NotNull FunctionalParser<R2, T> parser) {
    return second(this.then(parser));
  }

  @Override
  public @NotNull <R2> FunctionalParser<R, T> thenSkip(@NotNull FunctionalParser<R2, T> parser) {
    return first(this.then(parser));
  }

  @Override
  public @NotNull FunctionalParser<R, T> or(final @NotNull FunctionalParser<R, T> parser) {
    return new OrParser<>(this, parser);
  }

  @Override
  public @NotNull <R2> FunctionalParser<R2, T> map(final @NotNull Function<R, R2> f) {
    return new MapParser<>(this, f);
  }

  private static @NotNull <R, R2, T> FunctionalParser<R, T> first(final @NotNull FunctionalParser<Pair<R, R2>, T> parser) {
    return new FirstParser<>(parser);
  }

  private static @NotNull <R, R2, T> FunctionalParser<R2, T> second(final @NotNull FunctionalParser<Pair<R, R2>, T> parser) {
    return new SecondParser<>(parser);
  }

  private static @NotNull <T> FunctionalParser<Object, T> finished() {
    return new FinishedParser<>();
  }

  private static @NotNull <R, T> FunctionalParser<R, T> pure(final @Nullable R value) {
    return new PureParser<>(value);
  }

  private static class TokenParser<T> extends FunctionalParserBase<Token<T>, T> {
    private final @NotNull T myType;
    private final @Nullable String myText;

    TokenParser(@NotNull T type, @Nullable String text) {
      myType = type;
      myText = text;
    }

    @Override
    public @NotNull Pair<Token<T>, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      final int pos = state.getPos();
      if (pos >= tokens.size()) {
        throw new ParserException("No tokens left", state);
      }
      final Token<T> token = tokens.get(pos);
      if (token.getType().equals(myType) && (myText == null || token.getText().equals(myText))) {
        final int newPos = pos + 1;
        final State newState = new State(state, newPos, Math.max(newPos, state.getMax()));
        return Pair.create(token, newState);
      }
      final String expected = myText != null ? String.format("Token(<%s>, \"%s\")", myType, myText) : String.format("Token(<%s>)", myType);
      throw new ParserException(String.format("Expected %s, found %s", expected, token), state);
    }
  }

  private static class ManyParser<R, T> extends FunctionalParserBase<List<R>, T> {
    private final @NotNull FunctionalParser<R, T> myParser;

    ManyParser(@NotNull FunctionalParser<R, T> parser) {
      myParser = parser;
    }

    @Override
    public @NotNull Pair<List<R>, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      final List<R> list = new ArrayList<>();
      try {
        //noinspection InfiniteLoopStatement
        while (true) {
          final Pair<R, State> result = myParser.parse(tokens, state);
          state = result.getSecond();
          list.add(result.getFirst());
        }
      }
      catch (ParserException e) {
        return Pair.create(list, new State(state, state.getPos(), e.getState().getMax()));
      }
    }
  }

  private static class CachedParser<R, T> extends FunctionalParserBase<R, T> {
    private final @NotNull FunctionalParser<R, T> myParser;
    private @Nullable Object myKey;
    private final @NotNull Map<Integer, SoftReference<Pair<R, State>>> myCache;

    CachedParser(@NotNull FunctionalParser<R, T> parser) {
      myParser = parser;
      myKey = null;
      myCache = new HashMap<>();
    }

    @Override
    public @NotNull Pair<R, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      if (myKey != state.getKey()) {
        myKey = state.getKey();
        myCache.clear();
      }
      final SoftReference<Pair<R, State>> ref = myCache.get(state.getPos());
      final Pair<R, State> cached = dereference(ref);
      if (cached != null) {
        return cached;
      }
      final Pair<R, State> result = myParser.parse(tokens, state);
      myCache.put(state.getPos(), new SoftReference<>(result));
      return result;
    }
  }

  private static class OrParser<R, T> extends FunctionalParserBase<R, T> {
   private final @NotNull FunctionalParserBase<R, T> myFirst;
   private final @NotNull FunctionalParser<R, T> mySecond;

    OrParser(@NotNull FunctionalParserBase<R, T> first, @NotNull FunctionalParser<R, T> second) {
      myFirst = first;
      mySecond = second;
    }

    @Override
    public @NotNull Pair<R, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      try {
        return myFirst.parse(tokens, state);
      }
      catch (ParserException e) {
        return mySecond.parse(tokens, new State(state, state.getPos(), e.getState().getMax()));
      }
    }
  }

  private static class FirstParser<R, T, R2> extends FunctionalParserBase<R, T> {
    private final @NotNull FunctionalParser<Pair<R, R2>, T> myParser;

    FirstParser(@NotNull FunctionalParser<Pair<R, R2>, T> parser) {
      myParser = parser;
    }

    @Override
    public @NotNull Pair<R, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      final Pair<Pair<R, R2>, State> result = myParser.parse(tokens, state);
      return Pair.create(result.getFirst().getFirst(), result.getSecond());
    }
  }

  private static class SecondParser<R2, T, R> extends FunctionalParserBase<R2, T> {
    private final @NotNull FunctionalParser<Pair<R, R2>, T> myParser;

    SecondParser(@NotNull FunctionalParser<Pair<R, R2>, T> parser) {
      myParser = parser;
    }

    @Override
    public @NotNull Pair<R2, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      final Pair<Pair<R, R2>, State> result = myParser.parse(tokens, state);
      return Pair.create(result.getFirst().getSecond(), result.getSecond());
    }
  }

  private static class FinishedParser<T> extends FunctionalParserBase<Object, T> {
    @Override
    public @NotNull Pair<Object, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      final int pos = state.getPos();
      if (pos >= tokens.size()) {
        return Pair.create(null, state);
      }
      throw new ParserException(String.format("Expected end of input, found %s", tokens.get(pos)), state);
    }
  }

  private static class PureParser<R, T> extends FunctionalParserBase<R, T> {
    private final @Nullable R myValue;

    PureParser(@Nullable R value) {
      myValue = value;
    }

    @Override
    public @NotNull Pair<R, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      return Pair.create(myValue, state);
    }
  }

  private static class ThenParser<R, R2, T> extends FunctionalParserBase<Pair<R, R2>, T> {
    private final @NotNull FunctionalParser<R, T> myFirst;
    private final @NotNull FunctionalParser<R2, T> mySecond;

    ThenParser(@NotNull FunctionalParser<R, T> first, @NotNull FunctionalParser<R2, T> second) {
      myFirst = first;
      mySecond = second;
    }

    @Override
    public @NotNull Pair<Pair<R, R2>, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      final Pair<R, State> result1 = myFirst.parse(tokens, state);
      final Pair<R2, State> result2 = mySecond.parse(tokens, result1.getSecond());
      return Pair.create(Pair.create(result1.getFirst(), result2.getFirst()), result2.getSecond());
    }
  }

  private static class MapParser<R2, T, R> extends FunctionalParserBase<R2, T> {
    private final @NotNull FunctionalParserBase<R, T> myParser;
    private final @NotNull Function<R, R2> myFunction;

    MapParser(@NotNull FunctionalParserBase<R, T> parser, @NotNull Function<R, R2> function) {
      myParser = parser;
      myFunction = function;
    }

    @Override
    public @NotNull Pair<R2, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      final Pair<R, State> result = myParser.parse(tokens, state);
      return Pair.create(myFunction.fun(result.getFirst()), result.getSecond());
    }
  }
}
