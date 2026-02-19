// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.searching.usages

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens.CHARACTER_LITERAL
import org.jetbrains.kotlin.lexer.KtTokens.COMMENTS
import org.jetbrains.kotlin.lexer.KtTokens.FALSE_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.FLOAT_LITERAL
import org.jetbrains.kotlin.lexer.KtTokens.IDENTIFIER
import org.jetbrains.kotlin.lexer.KtTokens.INTEGER_LITERAL
import org.jetbrains.kotlin.lexer.KtTokens.KEYWORDS
import org.jetbrains.kotlin.lexer.KtTokens.NULL_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.STRINGS
import org.jetbrains.kotlin.lexer.KtTokens.TRUE_KEYWORD

class KotlinWordsScanner : DefaultWordsScanner(
    KotlinLexer(),
    TokenSet.create(IDENTIFIER).also { KEYWORDS },
    COMMENTS,
    TokenSet.create(INTEGER_LITERAL, CHARACTER_LITERAL, FLOAT_LITERAL, NULL_KEYWORD, TRUE_KEYWORD, FALSE_KEYWORD).also { STRINGS }
)