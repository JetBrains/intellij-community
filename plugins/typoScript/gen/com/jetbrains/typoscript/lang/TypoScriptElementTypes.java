/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

// This is a generated file. Not intended for manual editing.
package com.jetbrains.typoscript.lang;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.jetbrains.typoscript.lang.psi.impl.*;

public interface TypoScriptElementTypes {

  IElementType ASSIGNMENT = new TypoScriptTokenType("ASSIGNMENT");
  IElementType CODE_BLOCK = new TypoScriptTokenType("CODE_BLOCK");
  IElementType CONDITION_ELEMENT = new TypoScriptTokenType("CONDITION_ELEMENT");
  IElementType COPYING = new TypoScriptTokenType("COPYING");
  IElementType INCLUDE_STATEMENT_ELEMENT = new TypoScriptTokenType("INCLUDE_STATEMENT_ELEMENT");
  IElementType MULTILINE_VALUE_ASSIGNMENT = new TypoScriptTokenType("MULTILINE_VALUE_ASSIGNMENT");
  IElementType OBJECT_PATH = new TypoScriptTokenType("OBJECT_PATH");
  IElementType UNSETTING = new TypoScriptTokenType("UNSETTING");
  IElementType VALUE_MODIFICATION = new TypoScriptTokenType("VALUE_MODIFICATION");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
       if (type == ASSIGNMENT) {
        return new AssignmentImpl(node);
      }
      else if (type == CODE_BLOCK) {
        return new CodeBlockImpl(node);
      }
      else if (type == CONDITION_ELEMENT) {
        return new ConditionElementImpl(node);
      }
      else if (type == COPYING) {
        return new CopyingImpl(node);
      }
      else if (type == INCLUDE_STATEMENT_ELEMENT) {
        return new IncludeStatementElementImpl(node);
      }
      else if (type == MULTILINE_VALUE_ASSIGNMENT) {
        return new MultilineValueAssignmentImpl(node);
      }
      else if (type == OBJECT_PATH) {
        return new ObjectPathImpl(node);
      }
      else if (type == UNSETTING) {
        return new UnsettingImpl(node);
      }
      else if (type == VALUE_MODIFICATION) {
        return new ValueModificationImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
