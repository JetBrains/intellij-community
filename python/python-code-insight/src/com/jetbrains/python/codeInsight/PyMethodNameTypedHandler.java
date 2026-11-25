// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyKnownDecorator;
import com.jetbrains.python.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Adds appropriate first parameter to a freshly-typed method declaration.
 * <br/>
 * The platform calls the handler on the EDT, so the handler reads only the PSI of the file. It resolves no name and evaluates no type.
 */
@ApiStatus.Internal
public final class PyMethodNameTypedHandler extends TypedHandlerDelegate {
  private static final Pattern DEF_THEN_IDENTIFIER = Pattern.compile(".*\\bdef\\s+" + PyNames.IDENTIFIER_RE);
  private static final Set<String> KNOWN_METACLASSES = Set.of(PyNames.FQN.TYPE, PyNames.FQN.ABC_META, PyNames.FQN.ENUM_META, "enum.EnumType");

  @Override
  public @NotNull Result beforeCharTyped(char character, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file, @NotNull FileType fileType) {
    if (DumbService.isDumb(project) || !(fileType instanceof PythonFileType)) return Result.CONTINUE; // else we'd mess up with other file types!
    if (character == '(') {
      if (!PyCodeInsightSettings.getInstance().INSERT_SELF_FOR_METHODS) {
        return Result.CONTINUE;
      }
      final Document document = editor.getDocument();
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      final int offset = editor.getCaretModel().getOffset();
      final int lineNumber = document.getLineNumber(offset);

      final String linePrefix = document.getText(TextRange.create(document.getLineStartOffset(lineNumber), offset));
      if (!DEF_THEN_IDENTIFIER.matcher(linePrefix).matches()) {
        return Result.CONTINUE;
      }
      documentManager.commitDocument(document);

      final PsiElement token = file.findElementAt(offset - 1);
      if (token == null) return Result.CONTINUE; // sanity check: beyond EOL

      final ASTNode tokenNode = token.getNode();
      if (tokenNode != null && tokenNode.getElementType() == PyTokenTypes.IDENTIFIER) {
        final PsiElement maybeDef = PyPsiUtils.getPrevNonCommentSibling(token.getPrevSibling(), false);
        if (maybeDef != null) {
          final ASTNode defNode = maybeDef.getNode();
          if (defNode != null && defNode.getElementType() == PyTokenTypes.DEF_KEYWORD) {
            final PsiElement maybeFunc = token.getParent();
            if (maybeFunc instanceof PyFunction func) {
              final PyClass cls = func.getContainingClass();
              if (cls != null) {
                final String paramName = getFirstParameterName(func, cls);
                // TODO: only print the ")" if Settings require it
                final CommonCodeStyleSettings settings = CodeStyle.getLanguageSettings(file, PythonLanguage.getInstance());
                final StringBuilder textToType = new StringBuilder();
                textToType.append("(");
                if (!paramName.isEmpty()) {
                  if (settings.SPACE_WITHIN_METHOD_PARENTHESES) {
                    textToType.append(" ");
                  }
                  textToType.append(paramName);
                  if (settings.SPACE_WITHIN_METHOD_PARENTHESES) {
                    textToType.append(" ");
                  }
                }
                else if (settings.SPACE_WITHIN_EMPTY_METHOD_PARENTHESES) {
                  textToType.append(" ");
                }
                textToType.append(")");
                final int caretOffset = editor.getCaretModel().getOffset();
                final CharSequence chars = editor.getDocument().getCharsSequence();
                if (caretOffset == chars.length() || chars.charAt(caretOffset) != ':') {
                  textToType.append(':');
                }
                EditorModificationUtil.insertStringAtCaret(editor, textToType.toString(), true, 1 + paramName.length()); // right after param name
                return Result.STOP;
              }
            }
          }
        }
      }
    }
    return Result.CONTINUE; // the default
  }

  private static @NotNull String getFirstParameterName(@NotNull PyFunction method, @NotNull PyClass cls) {
    if (PyUtil.isNewMethod(method)) {
      return isMetaclass(cls) ? "typ" : "cls";
    }
    final PyFunction.Modifier modifier = getModifier(method);
    if (modifier == PyFunction.Modifier.CLASSMETHOD) return "cls";
    if (modifier == PyFunction.Modifier.STATICMETHOD) return "";
    return isMetaclass(cls) ? "cls" : "self";
  }

  /**
   * Returns the modifier that the decorator names and the name of {@code method} give.
   * Unlike {@link PyFunction#getModifier()}, it resolves no decorator and builds no type evaluation context.
   */
  private static @Nullable PyFunction.Modifier getModifier(@NotNull PyFunction method) {
    final PyDecoratorList decoratorList = method.getDecoratorList();
    if (decoratorList != null && method.getContainingFile() instanceof PyFile file) {
      final PyDecorator[] decorators = decoratorList.getDecorators();
      for (int i = decorators.length - 1; i >= 0; i--) {
        final QualifiedName name = decorators[i].getQualifiedName();
        if (name == null) continue;
        for (QualifiedName absoluteName : getAbsoluteNames(name, file)) {
          for (PyKnownDecorator decorator : PyKnownDecoratorUtil.asKnownDecorators(absoluteName)) {
            if (decorator.isClassMethod()) return PyFunction.Modifier.CLASSMETHOD;
            if (decorator.isStaticMethod()) return PyFunction.Modifier.STATICMETHOD;
          }
        }
      }
    }
    final String name = method.getName();
    final LanguageLevel level = LanguageLevel.forElement(method);
    if (PyNames.INIT_SUBCLASS.equals(name) && level.isAtLeast(LanguageLevel.PYTHON36) ||
        PyNames.CLASS_GETITEM.equals(name) && level.isAtLeast(LanguageLevel.PYTHON37)) {
      return PyFunction.Modifier.CLASSMETHOD;
    }
    return null;
  }

  /**
   * Returns the names that the top-level imports of {@code file} give to {@code name}.
   * A single name that the file neither imports nor defines at the top level is a builtin, so it stays as it is.
   * For any other name the method returns nothing.
   */
  private static @NotNull List<QualifiedName> getAbsoluteNames(@NotNull QualifiedName name, @NotNull PyFile file) {
    final List<QualifiedName> importedNames = PyKnownDecoratorUtil.importedQualifiedNames(name, file);
    if (importedNames == null) return List.of();
    if (!importedNames.isEmpty()) return importedNames;
    final String shortName = name.getLastComponent();
    if (name.getComponentCount() != 1 || file.findTopLevelFunction(shortName) != null || file.findTopLevelClass(shortName) != null ||
        file.findTopLevelAttribute(shortName) != null) {
      return List.of();
    }
    return List.of(name);
  }

  private static boolean isMetaclass(@NotNull PyClass cls) {
    return isMetaclass(cls, new HashSet<>());
  }

  /**
   * Tells if {@code cls} extends a known metaclass.
   * The method reads the superclass expressions, the imports and the top-level classes of the file. It resolves no name.
   */
  private static boolean isMetaclass(@NotNull PyClass cls, @NotNull Set<PyClass> visited) {
    if (!visited.add(cls) || !(cls.getContainingFile() instanceof PyFile file)) return false;
    for (PyExpression expression : cls.getSuperClassExpressions()) {
      if (expression instanceof PyKeywordArgument) continue;
      final QualifiedName name = PyPsiUtils.asQualifiedName(expression);
      if (name == null) continue;
      final List<QualifiedName> importedNames = PyKnownDecoratorUtil.importedQualifiedNames(name, file);
      if (importedNames == null) continue;
      if (!importedNames.isEmpty()) {
        if (ContainerUtil.exists(importedNames, importedName -> KNOWN_METACLASSES.contains(importedName.toString()))) return true;
        continue;
      }
      if (name.getComponentCount() != 1) continue;
      final PyClass ancestor = file.findTopLevelClass(name.getLastComponent());
      if (ancestor != null) {
        if (isMetaclass(ancestor, visited)) return true;
      }
      else if (PyNames.TYPE.equals(name.getLastComponent())) {
        return true;
      }
    }
    return false;
  }
}
