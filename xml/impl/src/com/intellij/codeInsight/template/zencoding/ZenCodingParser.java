/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.zencoding;

import com.intellij.codeInsight.template.zencoding.filters.ZenCodingFilter;
import com.intellij.codeInsight.template.zencoding.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.zencoding.nodes.*;
import com.intellij.codeInsight.template.zencoding.tokens.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
* @author Eugene.Kudelevsky
*/
public class ZenCodingParser {
  private final List<ZenCodingToken> myTokens;
  private int myIndex = 0;

  public ZenCodingParser(List<ZenCodingToken> tokens) {
    myTokens = tokens;
  }

  public int getIndex() {
    return myIndex;
  }

  @Nullable
  public ZenCodingNode parse() {
    ZenCodingNode add = parseAddOrMore();
    if (add == null) {
      return null;
    }
    ZenCodingToken filter = nextToken();
    ZenCodingNode result = add;
    while (filter instanceof FilterToken) {
      String suffix = ((FilterToken)filter).getSuffix();
      if (!checkFilterSuffix(suffix)) {
        return null;
      }
      result = new FilterNode(result, suffix);
      myIndex++;
      filter = nextToken();
    }
    return result;
  }

  public static boolean checkFilterSuffix(@NotNull String suffix) {
    for (ZenCodingGenerator generator : ZenCodingGenerator.getInstances()) {
      if (suffix.equals(generator.getSuffix())) {
        return true;
      }
    }
    for (ZenCodingFilter filter : ZenCodingFilter.getInstances()) {
      if (suffix.equals(filter.getSuffix())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private ZenCodingNode parseAddOrMore() {
    ZenCodingNode mul = parseMul();
    if (mul == null) {
      return null;
    }
    ZenCodingToken operationToken = nextToken();
    if (!(operationToken instanceof OperationToken)) {
      return mul;
    }
    char sign = ((OperationToken)operationToken).getSign();
    if (sign == '+') {
      myIndex++;
      ZenCodingNode add2 = parseAddOrMore();
      if (add2 == null) {
        return null;
      }
      return new AddOperationNode(mul, add2);
    }
    else if (sign == '>') {
      myIndex++;
      ZenCodingNode more2 = parseAddOrMore();
      if (more2 == null) {
        return null;
      }
      return new MoreOperationNode(mul, more2);
    }
    return null;
  }

  @Nullable
  private ZenCodingNode parseMul() {
    ZenCodingNode exp = parseExpressionInBraces();
    if (exp == null) {
      return null;
    }
    ZenCodingToken operationToken = nextToken();
    if (!(operationToken instanceof OperationToken)) {
      return exp;
    }
    if (((OperationToken)operationToken).getSign() != '*') {
      return exp;
    }
    myIndex++;
    ZenCodingToken numberToken = nextToken();
    if (numberToken instanceof NumberToken) {
      myIndex++;
      return new MulOperationNode(exp, ((NumberToken)numberToken).getNumber());
    }
    return new UnaryMulOperationNode(exp);
  }

  @Nullable
  private ZenCodingNode parseExpressionInBraces() {
    ZenCodingToken openingBrace = nextToken();
    if (openingBrace instanceof OpeningBraceToken) {
      myIndex++;
      ZenCodingNode add = parseAddOrMore();
      if (add == null) {
        return null;
      }
      ZenCodingToken closingBrace = nextToken();
      if (!(closingBrace instanceof ClosingBraceToken)) {
        return null;
      }
      myIndex++;
      return add;
    }
    ZenCodingToken templateToken = nextToken();
    if (templateToken instanceof TemplateToken) {
      myIndex++;
      return new TemplateNode((TemplateToken)templateToken);
    }
    return null;
  }

  @Nullable
  private ZenCodingToken nextToken() {
    if (myIndex < myTokens.size()) {
      return myTokens.get(myIndex);
    }
    return null;
  }
}
