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
package com.jetbrains.python.findUsages;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.VersionedWordsScanner;
import com.intellij.lang.cacheBuilder.WordOccurrence;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Processor;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonLexer;

/**
 * @author yole
 */
class PyWordsScanner extends VersionedWordsScanner {
  private volatile DefaultWordsScanner myDelegate;

  @Override
  public void processWords(CharSequence fileText, Processor<WordOccurrence> processor) {
    DefaultWordsScanner delegate = myDelegate;
    if (delegate == null) {
      myDelegate = delegate = new DefaultWordsScanner(new PythonLexer(),
                                         TokenSet.create(PyTokenTypes.IDENTIFIER),
                                         TokenSet.create(PyTokenTypes.END_OF_LINE_COMMENT),
                                         PyTokenTypes.STRING_NODES);
    }
    delegate.processWords(fileText, processor);
  }

  @Override
  public int getVersion() {
    return super.getVersion() + 1;
  }

}
