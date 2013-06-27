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
    return new FunctionalParserBase<Token<T>, T>() {
      @NotNull
      @Override
      public Pair<Token<T>, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
        final int pos = state.getPos();
        if (pos >= tokens.size()) {
          throw new ParserException("No tokens left", state);
        }
        final Token<T> token = tokens.get(pos);
        if (token.getType().equals(type) && (text == null || token.getText().equals(text))) {
          final int newPos = pos + 1;
          final State newState = new State(state, newPos, Math.max(newPos, state.getMax()));
          return Pair.create(token, newState);
        }
        final String expected = text != null ? String.format("Token(<%s>, \"%s\")", type, text) : String.format("Token(<%s>)", type);
        throw new ParserException(String.format("Expected %s, found %s", expected, token), state);
      }
    };
  }

  @NotNull
  public static <R, T> FunctionalParser<List<R>, T> many(@NotNull final FunctionalParser<R, T> parser) {
    return new FunctionalParserBase<List<R>, T>() {
      @NotNull
      @Override
      public Pair<List<R>, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
        final List<R> list = new ArrayList<R>();
        try {
          //noinspection InfiniteLoopStatement
          while (true) {
            final Pair<R, State> result = parser.parse(tokens, state);
            state = result.getSecond();
            list.add(result.getFirst());
          }
        }
        catch (ParserException e) {
          return Pair.create(list, new State(state, state.getPos(), e.getState().getMax()));
        }
      }
    };
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
    final FunctionalParser<R, T> thisParser = this;
    return new FunctionalParserBase<R, T>() {
      private Object myKey = null;
      private Map<Integer, SoftReference<Pair<R, State>>> myCache = new HashMap<Integer, SoftReference<Pair<R, State>>>();

      @NotNull
      @Override
      public Pair<R, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
        if (myKey != state.getKey()) {
          myKey = state.getKey();
          myCache.clear();
        }
        final SoftReference<Pair<R, State>> ref = myCache.get(state.getPos());
        if (ref != null) {
          final Pair<R, State> cached = ref.get();
          if (cached != null) {
            return cached;
          }
        }
        final Pair<R, State> result = thisParser.parse(tokens, state);
        myCache.put(state.getPos(), new SoftReference<Pair<R, State>>(result));
        return result;
      }
    };
  }

  @NotNull
  @Override
  public <R2> FunctionalParser<Pair<R, R2>, T> then(@NotNull final FunctionalParser<R2, T> parser) {
    final FunctionalParser<R, T> thisParser = this;
    return new FunctionalParserBase<Pair<R, R2>, T>() {
      @NotNull
      @Override
      public Pair<Pair<R, R2>, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
        final Pair<R, State> result1 = thisParser.parse(tokens, state);
        final Pair<R2, State> result2 = parser.parse(tokens, result1.getSecond());
        return Pair.create(Pair.create(result1.getFirst(), result2.getFirst()), result2.getSecond());
      }
    };
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
    final FunctionalParserBase<R, T> thisParser = this;
    return new FunctionalParserBase<R, T>() {
      @NotNull
      @Override
      public Pair<R, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
        try {
          return thisParser.parse(tokens, state);
        }
        catch (ParserException e) {
          return parser.parse(tokens, new State(state, state.getPos(), e.getState().getMax()));
        }
      }
    };
  }

  @NotNull
  @Override
  public <R2> FunctionalParser<R2, T> map(@NotNull final Function<R, R2> f) {
    final FunctionalParserBase<R, T> thisParser = this;
    return new FunctionalParserBase<R2, T>() {
      @NotNull
      @Override
      public Pair<R2, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
        final Pair<R, State> result = thisParser.parse(tokens, state);
        return Pair.create(f.fun(result.getFirst()), result.getSecond());
      }
    };
  }

  @NotNull
  private static <R, R2, T> FunctionalParser<R, T> first(@NotNull final FunctionalParser<Pair<R, R2>, T> parser) {
    return new FunctionalParserBase<R, T>() {
      @NotNull
      @Override
      public Pair<R, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
        final Pair<Pair<R, R2>, State> result = parser.parse(tokens, state);
        return Pair.create(result.getFirst().getFirst(), result.getSecond());
      }
    };
  }

  @NotNull
  private static <R, R2, T> FunctionalParser<R2, T> second(@NotNull final FunctionalParser<Pair<R, R2>, T> parser) {
    return new FunctionalParserBase<R2, T>() {
      @NotNull
      @Override
      public Pair<R2, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
        final Pair<Pair<R, R2>, State> result = parser.parse(tokens, state);
        return Pair.create(result.getFirst().getSecond(), result.getSecond());
      }
    };
  }

  @NotNull
  private static <T> FunctionalParser<Object, T> finished() {
    return new FunctionalParserBase<Object, T>() {
      @NotNull
      @Override
      public Pair<Object, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
        final int pos = state.getPos();
        if (pos >= tokens.size()) {
          return Pair.create(null, state);
        }
        throw new ParserException(String.format("Expected end of input, found %s", tokens.get(pos)), state);
      }
    };
  }

  @NotNull
  private static <R, T> FunctionalParser<R, T> pure(@Nullable final R value) {
    return new FunctionalParserBase<R, T>() {
      @NotNull
      @Override
      public Pair<R, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
        return Pair.create(value, state);
      }
    };
  }
}
