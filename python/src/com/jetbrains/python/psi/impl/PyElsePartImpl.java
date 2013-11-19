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
package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.PyElsePart;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.PyElementTypes;
import com.intellij.lang.ASTNode;

/**
 * User: dcheryasov
 * Date: Mar 15, 2009 9:40:35 PM
 */
public class PyElsePartImpl extends PyElementImpl implements PyElsePart {
  
  public PyElsePartImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyStatementList getStatementList() {
    ASTNode n = getNode().findChildByType(PyElementTypes.STATEMENT_LISTS);
    if (n != null) {
      return (PyStatementList)n.getPsi();
    }
    return null;
  }
}
