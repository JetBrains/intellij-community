package org.jetbrains.yaml.syntax.lexer

import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.util.lexer.FlexAdapter
import com.intellij.platform.syntax.util.lexer.MergingLexerAdapter
import org.jetbrains.yaml.syntax.YamlSyntaxTokenTypes

class YamlLexer: MergingLexerAdapter(MyFlexAdapter(_YamlLexer()), TOKENS_TO_MERGE)

private val TOKENS_TO_MERGE = syntaxElementTypeSetOf(YamlSyntaxTokenTypes.TEXT)

private class MyFlexAdapter(flex: _YamlLexer) : FlexAdapter(flex) {
    private val yamlFlex get() = super.flex as _YamlLexer

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        val newInitialState = if (initialState != DIRTY_STATE) {
            yamlFlex.cleanMyState()
            initialState
        }
        else {
            // That should not occur normally, but some complex lexers (e.g. black and white lexer)
            // require "suspending" of the lexer to pass some template language. In these cases we
            // believe that the same instance of the lexer would be restored (with its internal state)
             0
        }
        super.start(buffer, startOffset, endOffset, newInitialState)
    }

    override fun getState(): Int {
        val state =  super.getState()
        return if (state != 0 || yamlFlex.isCleanState()) state
        else DIRTY_STATE
    }
}

private const val DIRTY_STATE = 239