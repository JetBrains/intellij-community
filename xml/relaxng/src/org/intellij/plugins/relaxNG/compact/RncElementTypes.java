/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.compact;

import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.relaxNG.compact.psi.*;
import org.intellij.plugins.relaxNG.compact.psi.impl.*;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 01.08.2007
*/
public class RncElementTypes {

  public static final IElementType DOCUMENT = new RncElementTypeEx<>("DOCUMENT", RncDocument.class);

  public static final IElementType NS_DECL = new RncElementTypeEx<RncNsDecl>("NS_DECL", RncNsDeclImpl.class);
  public static final IElementType DATATYPES_DECL = new RncElementTypeEx<RncDecl>("DATATYPES_DECL", RncDeclImpl.class);

  public static final IElementType START = new RncElementType("START");
  public static final IElementType DIV = new RncElementTypeEx<RncDiv>("DIV", RncDivImpl.class);
  public static final IElementType INCLUDE = new RncElementTypeEx<RncInclude>("INCLUDE", RncIncludeImpl.class);

  public static final IElementType NAME_CLASS = new RncElementType("NAME_CLASS");
  public static final IElementType NAME_CLASS_CHOICE = new RncElementType("NAME_CLASS_CHOICE");
  public static final IElementType EXCEPT_NAME_CLASS = new RncElementType("EXCEPT_NAME_CLASS");

  public static final IElementType DEFINE = new RncElementTypeEx<RncDefine>("DEFINE", RncDefineImpl.class);
  public static final IElementType PATTERN = new RncElementType("PATTERN");
  public static final IElementType GROUP_PATTERN = new RncElementType("GROUP_PATTERN");
  public static final IElementType GRAMMAR_PATTERN = new RncElementTypeEx<RncGrammar>("GRAMMAR_PATTERN", RncGrammarImpl.class);
  public static final IElementType EMPTY_PATTERN = new RncElementType("EMPTY_PATTERN");
  public static final IElementType TEXT_PATTERN = new RncElementType("TEXT_PATTERN");
  public static final IElementType NOT_ALLOWED_PATTERN = new RncElementType("NOT_ALLOWED_PATTERN");
  public static final IElementType EXTERNAL_REF = new RncElementTypeEx<RncExternalRef>("EXTERNAL_REF", RncExternalRefImpl.class);
  public static final IElementType PARENT_REF = new RncElementTypeEx<RncParentRef>("PARENT_REF", RncParentRefImpl.class);
  public static final IElementType REF_PATTERN = new RncElementTypeEx<RncRef>("REF_PATTERN", RncRefImpl.class);
  public static final IElementType LIST_PATTERN = new RncElementType("LIST_PATTERN");
  public static final IElementType MIXED_PATTERN = new RncElementType("MIXED_PATTERN");

  public static final IElementType DATATYPE_PATTERN = new RncElementType("DATATYPE_PATTERN");
  public static final IElementType PARAM = new RncElementType("PARAM");

  public static final IElementType SEQUENCE = new RncElementType("SEQUENCE");
  public static final IElementType INTERLEAVE = new RncElementType("INTERLEAVE");
  public static final IElementType CHOICE = new RncElementType("CHOICE");
  public static final IElementType OPTIONAL = new RncElementType("OPTIONAL");
  public static final IElementType ZERO_OR_MORE = new RncElementType("ZERO_OR_MORE");
  public static final IElementType ONE_OR_MORE = new RncElementType("ONE_OR_MORE");

  public static final IElementType NAME = new RncElementTypeEx<RncName>("NAME", RncNameImpl.class);

  public static final IElementType ANNOTATION = new RncElementTypeEx<RncAnnotation>("ANNOTATION", RncAnnotationImpl.class);
  public static final IElementType ANNOTATION_ELEMENT = new RncElementTypeEx<RncAnnotation>("ANNOTATION_ELEMENT", RncAnnotationImpl.class);
  public static final IElementType FORWARD_ANNOTATION = new RncElementTypeEx<RncAnnotation>("FORWARD_ANNOTATION", RncAnnotationImpl.class);
}
