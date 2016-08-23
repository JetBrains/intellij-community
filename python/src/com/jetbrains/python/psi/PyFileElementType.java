/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.psi;

import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.io.StringRef;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.console.parsing.PyConsoleParser;
import com.jetbrains.python.console.parsing.PythonConsoleData;
import com.jetbrains.python.console.parsing.PythonConsoleLexer;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.impl.stubs.PyFileStubBuilder;
import com.jetbrains.python.psi.impl.stubs.PyFileStubImpl;
import com.jetbrains.python.psi.stubs.PyFileStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * @author yole
 */
public class PyFileElementType extends IStubFileElementType<PyFileStub> {
  public static PyFileElementType INSTANCE = new PyFileElementType(PythonLanguage.getInstance());

  protected PyFileElementType(Language language) {
    super(language);
  }

  @Override
  public StubBuilder getBuilder() {
    return new PyFileStubBuilder();
  }

  @Override
  public int getStubVersion() {
    // Don't forget to update versions of indexes that use the updated stub-based elements
    return 58;
  }

  @Nullable
  @Override
  public ASTNode parseContents(ASTNode node) {
    final LanguageLevel languageLevel = getLanguageLevel(node.getPsi());
    if (PydevConsoleRunner.isPythonConsole(node)) {
      return parseConsoleCode(node, PydevConsoleRunner.getPythonConsoleData(node));
    }
    else {
      final PsiElement psi = node.getPsi();
      if (psi != null) {
        final Project project = psi.getProject();
        final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
        final Language language = getLanguage();
        final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);
        if (parserDefinition == null) {
          return null;
        }
        final Lexer lexer = parserDefinition.createLexer(project);
        final PsiParser parser = parserDefinition.createParser(project);
        final PsiBuilder builder = factory.createBuilder(project, node, lexer, language, node.getChars());
        if (parser instanceof PyParser) {
          final PyParser pythonParser = (PyParser)parser;
          pythonParser.setLanguageLevel(languageLevel);
          if (languageLevel == LanguageLevel.PYTHON26 && psi.getContainingFile().getName().equals("__builtin__.py")) {
            pythonParser.setFutureFlag(StatementParsing.FUTURE.PRINT_FUNCTION);
          }
        }
        return parser.parse(this, builder).getFirstChildNode();
      }
      return null;
    }
  }

  @Nullable
  private ASTNode parseConsoleCode(@NotNull ASTNode node, PythonConsoleData consoleData) {
    final Lexer lexer = createConsoleLexer(node, consoleData);
    final PsiElement psi = node.getPsi();
    if (psi != null) {
      final Project project = psi.getProject();
      final PsiBuilderFactory factory = PsiBuilderFactory.getInstance();
      final PsiBuilder builder = factory.createBuilder(project, node, lexer, getLanguage(), node.getChars());
      final PyParser parser = new PyConsoleParser(consoleData);

      return parser.parse(this, builder).getFirstChildNode();
    }
    return null;
  }

  @Nullable
  private Lexer createConsoleLexer(ASTNode node, PythonConsoleData consoleData) {
    if (consoleData.isIPythonEnabled()) {
      return new PythonConsoleLexer();
    }
    else {
      final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(getLanguage());
      if (parserDefinition == null) {
        return null;
      }
      final PsiElement psi = node.getPsi();
      if (psi == null) {
        return null;
      }
      final Project project = psi.getProject();
      return parserDefinition.createLexer(project);
    }
  }

  private static LanguageLevel getLanguageLevel(PsiElement psi) {
    final PsiFile file = psi.getContainingFile();
    if (!(file instanceof PyFile)) {
      final PsiElement context = file.getContext();
      if (context != null) return getLanguageLevel(context);
      return LanguageLevel.getDefault();
    }
    return ((PyFile)file).getLanguageLevel();
  }

  @NotNull
  @Override
  public String getExternalId() {
    return "python.FILE";
  }

  @Override
  public void serialize(@NotNull PyFileStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    writeNullableList(dataStream, stub.getDunderAll());
    writeBitSet(dataStream, stub.getFutureFeatures());
    dataStream.writeName(stub.getDeprecationMessage());
  }

  @NotNull
  @Override
  public PyFileStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    List<String> all = readNullableList(dataStream);
    BitSet future_features = readBitSet(dataStream);
    StringRef deprecationMessage = dataStream.readName();
    return new PyFileStubImpl(all, future_features, deprecationMessage);
  }

  private static BitSet readBitSet(StubInputStream dataStream) throws IOException {
    // NOTE: here we assume that bitset has no more than 32 bits so that the value fits into an int.
    BitSet ret = new BitSet(32); // see PyFileStubImpl: we assume that all bits fit into an int
    int bits = dataStream.readInt();
    for (int i = 0; i < 32; i += 1) {
      boolean bit = (bits & (1 << i)) != 0;
      ret.set(i, bit);
    }
    return ret;
  }

  private static void writeBitSet(StubOutputStream dataStream, BitSet bitset) throws IOException {
    // NOTE: here we assume that bitset has no more than 32 bits so that the value fits into an int.
    int result = 0;
    for (int i = 0; i < 32; i += 1) {
      int bit = (bitset.get(i) ? 1 : 0) << i;
      result |= bit;
    }
    dataStream.writeInt(result);
  }

  public static void writeNullableList(StubOutputStream dataStream, final List<String> names) throws IOException {
    if (names == null) {
      dataStream.writeBoolean(false);
    }
    else {
      dataStream.writeBoolean(true);
      dataStream.writeVarInt(names.size());
      for (String name : names) {
        dataStream.writeName(name);
      }
    }
  }

  @Nullable
  public static List<String> readNullableList(StubInputStream dataStream) throws IOException {
    boolean hasNames = dataStream.readBoolean();
    List<String> names = null;
    if (hasNames) {
      int size = dataStream.readVarInt();
      names = new ArrayList<>(size);
      for (int i = 0; i < size; i++) {
        names.add(dataStream.readName().getString());
      }
    }
    return names;
  }
}
