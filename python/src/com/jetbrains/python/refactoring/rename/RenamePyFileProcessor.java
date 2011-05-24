package com.jetbrains.python.refactoring.rename;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenamePsiFileProcessor;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportStatementBase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class RenamePyFileProcessor extends RenamePsiFileProcessor {
  @Override
  public boolean canProcessElement(@NotNull PsiElement element) {
    return element instanceof PyFile;
  }

  @Override
  public void findCollisions(PsiElement element,
                             final String newName,
                             Map<? extends PsiElement, String> allRenames,
                             List<UsageInfo> result) {
    final String newFileName = FileUtil.getNameWithoutExtension(newName);
    if (!PyNames.isIdentifier(newFileName)) {
      List<UsageInfo> usages = new ArrayList<UsageInfo>(result);
      for (UsageInfo usageInfo : usages) {
        final PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(usageInfo.getElement(), PyImportStatementBase.class);
        if (importStatement != null) {
          result.add(new UnresolvableCollisionUsageInfo(importStatement, element) {
            @Override
            public String getDescription() {
              return "The name '" + newFileName + "' is not a valid Python identifier. Cannot update import statement in '" +
                     importStatement.getContainingFile().getName() + "'";
            }
          });
        }
      }
    }
  }
}
