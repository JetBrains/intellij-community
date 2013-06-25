package com.jetbrains.python.psi.types.functionalParser;

import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
* @author vlan
*/
public interface FunctionalParser<R, T> {
  @NotNull
  R parse(@NotNull List<Token<T>> tokens) throws ParserException;

  @NotNull
  Pair<R, FunctionalParserBase.State> parse(@NotNull List<Token<T>> tokens,
                                            @NotNull FunctionalParserBase.State state) throws ParserException;

  @NotNull
  <R2> FunctionalParser<Pair<R, R2>, T> then(@NotNull FunctionalParser<R2, T> parser);

  @NotNull
  <R2> FunctionalParser<R2, T> skipThen(@NotNull FunctionalParser<R2, T> parser);

  @NotNull
  <R2> FunctionalParser<R, T> thenSkip(@NotNull FunctionalParser<R2, T> parser);

  @NotNull
  FunctionalParser<R, T> or(@NotNull FunctionalParser<R, T> parser);

  @NotNull
  <R2> FunctionalParser<R2, T> map(@NotNull Function<R, R2> f);

  @NotNull
  FunctionalParser<R, T> endOfInput();

  @NotNull
  FunctionalParser<R, T> named(@NotNull String name);

  @NotNull
  FunctionalParser<R, T> cached();

  class State {
    private final int myPos;
    private final int myMax;
    private final Object myKey;

    State() {
      myKey = new Object();
      myPos = 0;
      myMax = 0;
    }

    State(@NotNull State state, int pos, int max) {
      myKey = state.myKey;
      myPos = pos;
      myMax = max;
    }

    public int getPos() {
      return myPos;
    }

    public int getMax() {
      return myMax;
    }

    public Object getKey() {
      return myKey;
    }
  }
}
