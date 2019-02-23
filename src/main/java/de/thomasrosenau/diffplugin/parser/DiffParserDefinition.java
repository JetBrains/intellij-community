/*
 Copyright 2019 Thomas Rosenau

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package de.thomasrosenau.diffplugin.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import de.thomasrosenau.diffplugin.DiffLanguage;
import de.thomasrosenau.diffplugin.lexer.DiffLexerAdapter;
import de.thomasrosenau.diffplugin.psi.DiffFile;
import de.thomasrosenau.diffplugin.psi.DiffTypes;
import org.jetbrains.annotations.NotNull;

public class DiffParserDefinition implements ParserDefinition {

    private static final IFileElementType FILE = new IFileElementType(DiffLanguage.INSTANCE);

    @NotNull
    @Override
    public Lexer createLexer(Project project) {
        return new DiffLexerAdapter();
    }

    @NotNull
    public PsiParser createParser(final Project project) {
        return new DiffParser();
    }

    @Override
    public IFileElementType getFileNodeType() {
        return FILE;
    }

    @NotNull
    public TokenSet getCommentTokens() {
        return TokenSet.EMPTY;
    }

    @NotNull
    public TokenSet getStringLiteralElements() {
        return TokenSet.EMPTY;
    }

    @NotNull
    public PsiElement createElement(ASTNode node) {
        return DiffTypes.Factory.createElement(node);
    }

    public PsiFile createFile(FileViewProvider viewProvider) {
        return new DiffFile(viewProvider);
    }
}
