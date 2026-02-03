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
package com.jetbrains.python.documentation.docstrings

import com.jetbrains.python.toolbox.Substring

class SphinxDocString(docstringText: Substring) : TagBasedDocString(docstringText, TAG_PREFIX) {
  override fun getKeywordArguments(): MutableList<String?> {
    return toUniqueStrings(keywordArgumentSubstrings)
  }

  override fun getKeywordArgumentDescription(paramName: String?): String? {
    if (paramName == null) {
      return null
    }
    return concatTrimmedLines(getTagValue(KEYWORD_ARGUMENT_TAGS, paramName))
  }

  override fun getReturnType(): String? {
    return concatTrimmedLines(returnTypeSubstring)
  }

  override fun getParamType(paramName: String?): String? {
    return concatTrimmedLines(getParamTypeSubstring(paramName))
  }

  override fun getParamDescription(paramName: String?): String? {
    return if (paramName != null) concatTrimmedLines(getTagValue(PARAM_TAGS, paramName)) else null
  }

  override fun getReturnDescription(): String? {
    return concatTrimmedLines(getTagValue(*RETURN_TAGS))
  }

  override fun getRaisedExceptions(): MutableList<String?> {
    return toUniqueStrings(getTagArguments(*RAISES_TAGS))
  }

  override fun getRaisedExceptionDescription(exceptionName: String?): String? {
    if (exceptionName == null) {
      return null
    }
    return concatTrimmedLines(getTagValue(RAISES_TAGS, exceptionName))
  }

  override fun getAttributeDescription(): String? {
    return concatTrimmedLines(getTagValue(*VARIABLE_TAGS))
  }

  override fun getKeywordArgumentSubstrings(): MutableList<Substring?> {
    return getTagArguments(*KEYWORD_ARGUMENT_TAGS)
  }

  override fun getReturnTypeSubstring(): Substring? {
    return getTagValue("rtype")
  }

  override fun getParamTypeSubstring(paramName: String?): Substring? {
    return if (paramName == null) getTagValue("type") else getTagValue("type", paramName)
  }

  override fun getDescription(): String {
    return myDescription.replace("\n".toRegex(), "<br/>")
  }

  override fun getAttributeDescription(attrName: String?): String? {
    return if (attrName != null) concatTrimmedLines(getTagValue(VARIABLE_TAGS, attrName)) else null
  }

  companion object {
    val KEYWORD_ARGUMENT_TAGS: Array<String> = arrayOf<String>("keyword", "key")
    @JvmField
    val ALL_TAGS: Array<String> = arrayOf<String>(":param", ":parameter", ":arg", ":argument", ":keyword", ":key",
                                                   ":type", ":raise", ":raises", ":var", ":cvar", ":ivar",
                                                   ":return", ":returns", ":rtype", ":except", ":exception")
    const val TAG_PREFIX: String = ":"

    private fun concatTrimmedLines(s: Substring?): String? {
      return s?.concatTrimmedLines(" ")
    }
  }
}
