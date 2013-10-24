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
package com.jetbrains.rest;

import com.intellij.psi.tree.IFileElementType;

/**
 * User : catherine
 */
public interface RestElementTypes {
  IFileElementType REST_FILE = new IFileElementType("REST_FILE", RestLanguage.INSTANCE);
  RestElementType REFERENCE_TARGET = new RestElementType("REFERENCE");
  RestElementType DIRECTIVE_BLOCK = new RestElementType("DIRECTIVE_BLOCK");
  RestElementType INLINE_BLOCK = new RestElementType("INLINE_BLOCK");

  RestElementType LINE_TEXT = new RestElementType("LINE_TEXT");
  RestElementType FIELD_LIST = new RestElementType("FIELD_LIST");
}

