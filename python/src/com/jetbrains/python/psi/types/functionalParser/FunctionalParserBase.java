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
import com.intellij.reference.SoftReference;
import com.intellij.util.Function;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
* @author vlan
*/
public abstract class FunctionalParserBase<R, T> implements FunctionalParser<R, T> {
  @Nullable private String myName = null;

  @Override
  public String toString() {
    return myName != null ? String.format("<%s>", myName) : super.toString();
  }

  @NotNull
  @Override
  public R parse(@NotNull List<Token<T>> tokens) throws ParserException {
    return parse(tokens, new State()).getFirst();
  }

  @NotNull
  public static <T> FunctionalParser<Token<T>, T> token(@NotNull T type) {
    return token(type, null);
  }

  @NotNull
  public static <T> FunctionalParser<Token<T>, T> token(@NotNull final T type, @Nullable final String text) {
    return new TokenParser<>(type, text);
  }

  @NotNull
  public static <R, T> FunctionalParser<List<R>, T> many(@NotNull final FunctionalParser<R, T> parser) {
    return new ManyParser<>(parser);
  }

  @NotNull
  public static <R, T> FunctionalParser<R, T> maybe(@NotNull final FunctionalParser<R, T> parser) {
    return parser.or(FunctionalParserBase.<R, T>pure(null));
  }

  @NotNull
  @Override
  public FunctionalParser<R, T> endOfInput() {
    return this.thenSkip(FunctionalParserBase.<T>finished());
  }

  @NotNull
  @Override
  public FunctionalParser<R, T> named(@NotNull String name) {
    myName = name;
    return this;
  }

  @NotNull
  @Override
  public FunctionalParser<R, T> cached() {
    return new CachedParser<>(this);
  }

  @NotNull
  @Override
  public <R2> FunctionalParser<Pair<R, R2>, T> then(@NotNull final FunctionalParser<R2, T> parser) {
    return new ThenParser<>(this, parser);
  }

  @NotNull
  @Override
  public <R2> FunctionalParser<R2, T> skipThen(@NotNull final FunctionalParser<R2, T> parser) {
    return second(this.then(parser));
  }

  @NotNull
  @Override
  public <R2> FunctionalParser<R, T> thenSkip(@NotNull FunctionalParser<R2, T> parser) {
    return first(this.then(parser));
  }

  @NotNull
  @Override
  public FunctionalParser<R, T> or(@NotNull final FunctionalParser<R, T> parser) {
    return new OrParser<>(this, parser);
  }

  @NotNull
  @Override
  public <R2> FunctionalParser<R2, T> map(@NotNull final Function<R, R2> f) {
    return new MapParser<>(this, f);
  }

  @NotNull
  private static <R, R2, T> FunctionalParser<R, T> first(@NotNull final FunctionalParser<Pair<R, R2>, T> parser) {
    return new FirstParser<>(parser);
  }

  @NotNull
  private static <R, R2, T> FunctionalParser<R2, T> second(@NotNull final FunctionalParser<Pair<R, R2>, T> parser) {
    return new SecondParser<>(parser);
  }

  @NotNull
  private static <T> FunctionalParser<Object, T> finished() {
    return new FinishedParser<>();
  }

  @NotNull
  private static <R, T> FunctionalParser<R, T> pure(@Nullable final R value) {
    return new PureParser<>(value);
  }

  private static class TokenParser<T> extends FunctionalParserBase<Token<T>, T> {
    @NotNull private final T myType;
    @Nullable private final String myText;

    public TokenParser(@NotNull T type, @Nullable String text) {
      myType = type;
      myText = text;
    }

    @NotNull
    @Override
    public Pair<Token<T>, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
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
    @NotNull private final FunctionalParser<R, T> myParser;

    public ManyParser(@NotNull FunctionalParser<R, T> parser) {
      myParser = parser;
    }

    @NotNull
    @Override
    public Pair<List<R>, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
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
    @NotNull private final FunctionalParser<R, T> myParser;
    @Nullable private Object myKey;
    @NotNull private Map<Integer, SoftReference<Pair<R, State>>> myCache;

    public CachedParser(@NotNull FunctionalParser<R, T> parser) {
      myParser = parser;
      myKey = null;
      myCache = new HashMap<>();
    }

    @NotNull
    @Override
    public Pair<R, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      if (myKey != state.getKey()) {
        myKey = state.getKey();
        myCache.clear();
      }
      final SoftReference<Pair<R, State>> ref = myCache.get(state.getPos());
      final Pair<R, State> cached = SoftReference.dereference(ref);
      if (cached != null) {
        return cached;
      }
      final Pair<R, State> result = myParser.parse(tokens, state);
      myCache.put(state.getPos(), new SoftReference<>(result));
      return result;
    }
  }

  private static class OrParser<R, T> extends FunctionalParserBase<R, T> {
   @NotNull private final FunctionalParserBase<R, T> myFirst;
   @NotNull private final FunctionalParser<R, T> mySecond;

    public OrParser(@NotNull FunctionalParserBase<R, T> first, @NotNull FunctionalParser<R, T> second) {
      myFirst = first;
      mySecond = second;
    }

    @NotNull
    @Override
    public Pair<R, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      try {
        return myFirst.parse(tokens, state);
      }
      catch (ParserException e) {
        return mySecond.parse(tokens, new State(state, state.getPos(), e.getState().getMax()));
      }
    }
  }

  private static class FirstParser<R, T, R2> extends FunctionalParserBase<R, T> {
    @NotNull private final FunctionalParser<Pair<R, R2>, T> myParser;

    public FirstParser(@NotNull FunctionalParser<Pair<R, R2>, T> parser) {
      myParser = parser;
    }

    @NotNull
    @Override
    public Pair<R, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      final Pair<Pair<R, R2>, State> result = myParser.parse(tokens, state);
      return Pair.create(result.getFirst().getFirst(), result.getSecond());
    }
  }

  private static class SecondParser<R2, T, R> extends FunctionalParserBase<R2, T> {
    @NotNull private final FunctionalParser<Pair<R, R2>, T> myParser;

    public SecondParser(@NotNull FunctionalParser<Pair<R, R2>, T> parser) {
      myParser = parser;
    }

    @NotNull
    @Override
    public Pair<R2, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      final Pair<Pair<R, R2>, State> result = myParser.parse(tokens, state);
      return Pair.create(result.getFirst().getSecond(), result.getSecond());
    }
  }

  private static class FinishedParser<T> extends FunctionalParserBase<Object, T> {
    @NotNull
    @Override
    public Pair<Object, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      final int pos = state.getPos();
      if (pos >= tokens.size()) {
        return Pair.create(null, state);
      }
      throw new ParserException(String.format("Expected end of input, found %s", tokens.get(pos)), state);
    }
  }

  private static class PureParser<R, T> extends FunctionalParserBase<R, T> {
    @Nullable private final R myValue;

    public PureParser(@Nullable R value) {
      myValue = value;
    }

    @NotNull
    @Override
    public Pair<R, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      return Pair.create(myValue, state);
    }
  }

  private static class ThenParser<R, R2, T> extends FunctionalParserBase<Pair<R, R2>, T> {
    @NotNull private final FunctionalParser<R, T> myFirst;
    @NotNull private final FunctionalParser<R2, T> mySecond;

    public ThenParser(@NotNull FunctionalParser<R, T> first, @NotNull FunctionalParser<R2, T> second) {
      myFirst = first;
      mySecond = second;
    }

    @NotNull
    @Override
    public Pair<Pair<R, R2>, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      final Pair<R, State> result1 = myFirst.parse(tokens, state);
      final Pair<R2, State> result2 = mySecond.parse(tokens, result1.getSecond());
      return Pair.create(Pair.create(result1.getFirst(), result2.getFirst()), result2.getSecond());
    }
  }

  private static class MapParser<R2, T, R> extends FunctionalParserBase<R2, T> {
    @NotNull private final FunctionalParserBase<R, T> myParser;
    @NotNull private final Function<R, R2> myFunction;

    public MapParser(@NotNull FunctionalParserBase<R, T> parser, @NotNull Function<R, R2> function) {
      myParser = parser;
      myFunction = function;
    }

    @NotNull
    @Override
    public Pair<R2, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
      final Pair<R, State> result = myParser.parse(tokens, state);
      return Pair.create(myFunction.fun(result.getFirst()), result.getSecond());
    }
  }
}
