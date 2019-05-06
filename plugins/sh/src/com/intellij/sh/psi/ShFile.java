package com.intellij.sh.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.sh.ShFileType;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.lexer.ShTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShFile extends PsiFileBase {
  public ShFile(@NotNull FileViewProvider viewProvider) {
    super(viewProvider, ShLanguage.INSTANCE);
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return ShFileType.INSTANCE;
  }

  @Nullable
  private String findShebangInner() {
    ASTNode shebang = getNode().findChildByType(ShTokenTypes.SHEBANG);
    return shebang != null ? shebang.getText() : null;
  }

  @Nullable
  public String findShebang() {
    return CachedValuesManager.getCachedValue(this, () -> CachedValueProvider.Result.create(findShebangInner(), this));
  }
}