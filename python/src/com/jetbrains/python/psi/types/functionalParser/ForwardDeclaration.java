package com.jetbrains.python.psi.types.functionalParser;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
* @author vlan
*/
public class ForwardDeclaration<R, T> extends FunctionalParserBase<R, T> {
  private FunctionalParser<R, T> myParser = null;

  @NotNull
  public static <R, T> ForwardDeclaration<R, T> create() {
    return new ForwardDeclaration<R, T>();
  }

  @NotNull
  public ForwardDeclaration<R, T> define(@NotNull FunctionalParser<R, T> parser) {
    myParser = parser;
    return this;
  }

  @NotNull
  @Override
  public Pair<R, State> parse(@NotNull List<Token<T>> tokens, @NotNull State state) throws ParserException {
    if (myParser != null) {
      return myParser.parse(tokens, state);
    }
    throw new IllegalStateException("Undefined forward parser declaration");
  }
}
