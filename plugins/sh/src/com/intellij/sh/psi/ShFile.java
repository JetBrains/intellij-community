// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.sh.ShFileType;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.ShTypes;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ShFile extends PsiFileBase {
  public ShFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, ShLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return ShFileType.INSTANCE;
  }

  public Map<PsiElement, ShFunctionName> findFunctions() {
    return CachedValuesManager.getCachedValue(this, () ->
      CachedValueProvider.Result.create(new ShFunctionInnerResolver().findFunctionsInner(this), this));
  }

  @Nullable
  public String findShebang() {
    return CachedValuesManager.getCachedValue(this, () -> CachedValueProvider.Result.create(findShebangInner(), this));
  }

  @Nullable
  private String findShebangInner() {
    ASTNode shebang = getNode().findChildByType(ShTypes.SHEBANG);
    return shebang != null ? shebang.getText() : null;
  }

  private static class ShFunctionInnerResolver {
    private final Map<String, FunctionContext> myExecutionContext;
    private final Map<PsiElement, ShFunctionName> myFunctionMapping;

    private ShFunctionInnerResolver() {
      myExecutionContext = new HashMap<>();
      myFunctionMapping = new HashMap<>();
    }

    public Map<PsiElement, ShFunctionName> findFunctionsInner(@NotNull ShFile shFile) {
      // Execution context should be build, not check inner function if it was not called
      checkChildren(shFile, false);
      checkUnvisitedFunctions();
      return myFunctionMapping;
    }

    private void checkUnvisitedFunctions() {
      // We should visit all not visited function one by one with checking inner function also
      ContainerUtil.filter(myExecutionContext.values(), it -> !it.visited).forEach(notVisitedFunction -> {
        notVisitedFunction.visited = true;
        checkChildren(notVisitedFunction.function, true);
      });
    }

    private void checkChildren(PsiElement element, boolean visitInnerFunction) {
      if (element == null) return;
      for (PsiElement child : element.getChildren()) {
        buildExecutionContext(skipUnnecessaryNodes(child), visitInnerFunction);
      }
    }

    private void buildExecutionContext(PsiElement element, boolean visitInnerFunction) {
      if (element == null) return;
      if (element instanceof ShFunctionDefinition) {
        // If we meet function definition, should add it to the context like a not visited node
        handleFunction((ShFunctionDefinition)element, visitInnerFunction);
        return;
      }
      if (element instanceof ShGenericCommandDirective) {
        // Check if command linked to function which we searching for. If linked function is not visited, we should check
        // all its entire function and add them to the execution context
        ShGenericCommandDirective genericCommand = (ShGenericCommandDirective)element;
        ShLiteral literal = genericCommand.getLiteral();
        if (literal != null) {
          String literalText = literal.getText();
          FunctionContext functionContext = myExecutionContext.get(literalText);
          if (functionContext == null) {
            myFunctionMapping.put(literal, null);
            return;
          }
          myFunctionMapping.put(literal, functionContext.function.getFunctionName());
          if (!functionContext.visited) {
            functionContext.visited = true;
            checkChildren(functionContext.function, visitInnerFunction);
          }
          return;
        }
      }
      checkChildren(element, visitInnerFunction);
    }

    private static PsiElement skipUnnecessaryNodes(@NotNull PsiElement element) {
      PsiElement nextElement = element;
      while (shouldBeSkipped(nextElement)) {
        nextElement = nextElement.getChildren()[0];
      }
      return nextElement;
    }

    private static boolean shouldBeSkipped(@NotNull PsiElement element) {
      return element.getChildren().length == 1 && !(element instanceof ShGenericCommandDirective || element instanceof ShFunctionDefinition);
    }

    private void handleFunction(@NotNull ShFunctionDefinition functionDefinition, boolean visitInnerFunction) {
      ShFunctionName functionName = functionDefinition.getFunctionName();
      assert functionName != null;
      FunctionContext functionContext = new FunctionContext(functionDefinition);
      myExecutionContext.put(functionName.getText(), functionContext);
      if (!visitInnerFunction) return;
      functionContext.visited = true;
      checkChildren(functionDefinition, true);
    }

    private static class FunctionContext {
      private final ShFunctionDefinition function;
      private boolean visited;

      private FunctionContext(ShFunctionDefinition function) {
        this.function = function;
      }
    }
  }
}