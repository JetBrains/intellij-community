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
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
* @author vlan
*/
public class ForwardDeclaration<R, T> extends FunctionalParserBase<R, T> {
  private FunctionalParser<R, T> myParser = null;

  @NotNull
  public static <R, T> ForwardDeclaration<R, T> create() {
    return new ForwardDeclaration<>();
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
