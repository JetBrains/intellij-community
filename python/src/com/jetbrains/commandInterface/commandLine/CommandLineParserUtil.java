/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.commandInterface.commandLine;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Tool to be used in parser generation to handle "=" for long option
 *
 * @author Ilya.Kazakevich
 */
final class CommandLineParserUtil extends GeneratedParserUtilBase {
  private CommandLineParserUtil() {
  }

  static void bound_argument(@NotNull final PsiBuilder b, final int i) {
    final IElementType tokenType = b.getTokenType();
    final IElementType leftElement = b.rawLookup(-1);
    final IElementType rightElement = b.rawLookup(1);
    if (leftElement == null || TokenType.WHITE_SPACE.equals(leftElement)) {
      return;
    }

    /**
     * At '=' position: if no whitespace to left and right, we move to argument.
     * And we report error if whitespace to the left.
     */
    if (tokenType == CommandLineElementTypes.EQ) {
      if (leftElement.equals(CommandLineElementTypes.LONG_OPTION_NAME_TOKEN)) {
        if (rightElement == null || TokenType.WHITE_SPACE.equals(rightElement)) {
          b.error("Space between argument is its value is unexpected");
        }
        b.advanceLexer();
      }
    }
  }
}
