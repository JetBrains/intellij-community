// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.searching.usages.dialogs;

import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.findUsages.KotlinFindUsagesSupport;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.psi.psiUtil.KtPsiUtilKt;

import javax.swing.*;
import java.awt.*;

final class Utils {
    private Utils() {
    }

    public static void configureLabelComponent(
            @NotNull SimpleColoredComponent coloredComponent,
            @NotNull KtNamedDeclaration declaration
    ) {
        @SuppressWarnings("HardCodedStringLiteral")
        String renderedDeclaration = KotlinFindUsagesSupport.Companion.tryRenderDeclarationCompactStyle(declaration);
        if (renderedDeclaration != null) {
            coloredComponent.append(renderedDeclaration);
        }
    }

    static boolean renameCheckbox(@NotNull JPanel panel, @NotNull String srcText, @Nls @NotNull String destText) {
        JCheckBox checkbox = findCheckbox(panel, srcText);
        if (checkbox != null) {
            checkbox.setText(destText);
            return true;
        }
        return false;
    }

    static void removeCheckbox(@NotNull JPanel panel, @NotNull String srcText) {
        JCheckBox checkbox = findCheckbox(panel, srcText);
        if (checkbox != null) {
            panel.remove(checkbox.getParent());
        }
    }

    private static @Nullable JCheckBox findCheckbox(@NotNull JPanel panel, @NotNull String srcText) {
        JCheckBox checkBox = null;
        for (Component component : panel.getComponents()) {
            if (component instanceof JCheckBox jCheckBox) {
                if (jCheckBox.getText().equals(srcText)) {
                    checkBox = jCheckBox;
                }
                break;
            }
            if (component instanceof JPanel jPanel) {
                checkBox = findCheckbox(jPanel, srcText);
                if (checkBox != null) break;
            }
        }
        return checkBox;
    }

  private static boolean isInInterface(KtNamedDeclaration declaration) {
    return KtPsiUtilKt.getContainingClassOrObject(declaration) instanceof KtClass ktClass && ktClass.isInterface();
  }

  static boolean isOpen(KtNamedDeclaration declaration) {
    return
      isInInterface(declaration) ||
      declaration.hasModifier(KtTokens.OPEN_KEYWORD) ||
      declaration.hasModifier(KtTokens.ABSTRACT_KEYWORD) ||
      KtPsiUtilKt.getContainingClassOrObject(declaration) instanceof KtClass ktClass &&
      (ktClass.hasModifier(KtTokens.OPEN_KEYWORD) || ktClass.hasModifier(KtTokens.ABSTRACT_KEYWORD)) &&
      declaration.hasModifier(KtTokens.OVERRIDE_KEYWORD) &&
      !declaration.hasModifier(KtTokens.FINAL_KEYWORD);
  }


  static boolean isAbstract(KtNamedDeclaration declaration) {
    return declaration.hasModifier(KtTokens.ABSTRACT_KEYWORD) || isAbstractInInterface(declaration);
  }

  static boolean isAbstractInInterface(KtNamedDeclaration declaration) {
    if (!isInInterface(declaration)) {
      return false;
    }
    if (declaration instanceof KtProperty ktProperty) {
      return !ktProperty.hasInitializer() && !ktProperty.hasDelegate() && ktProperty.getAccessors().isEmpty();
    }
    if (declaration instanceof KtFunction ktFunction) {
      return !ktFunction.hasBody();
    }
    return false;
  }
}
