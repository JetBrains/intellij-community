package org.jetbrains.yaml;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author oleg
 */
public class YAMLPairedBraceMatcher implements PairedBraceMatcher, YAMLTokenTypes {
    private static final BracePair[] PAIRS = new BracePair[]{
            new BracePair(LBRACE, RBRACE, true),
            new BracePair(LBRACKET, RBRACKET, true),
    };

    @NotNull
    public BracePair[] getPairs() {
        return PAIRS;
    }

    public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType iElementType, @Nullable IElementType iElementType1) {
        return true;
    }

  public int getCodeConstructStart(final PsiFile file, final int openingBraceOffset) {
    return openingBraceOffset;
  }
}

