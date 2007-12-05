package com.intellij.psi.impl;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import com.intellij.util.*;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class ElementBase extends UserDataHolderBase implements Iconable {
  public static final int FLAGS_LOCKED = 0x800;

  @Nullable
  public Icon getIcon(int flags) {
    if (!(this instanceof PsiElement)) return null;

    final PsiElement element = (PsiElement)this;

    final Icon providersIcon = PsiIconUtil.getProvidersIcon(element, flags);
    if (providersIcon != null) {
      final RowIcon rowIcon = providersIcon instanceof RowIcon ? (RowIcon)providersIcon : createLayeredIcon(providersIcon, flags);
      return addVisibilityIcon(element, flags, rowIcon);
    }

    return getElementIcon(flags);
  }

  protected Icon getElementIcon(final int flags) {
    final PsiElement element = (PsiElement)this;
    boolean visibilityAdded = false;
    RowIcon baseIcon;
    final boolean isLocked = (flags & ICON_FLAG_READ_STATUS) != 0 && !element.isWritable();
    int elementFlags = isLocked ? FLAGS_LOCKED : 0;
    if (element instanceof ItemPresentation && ((ItemPresentation)element).getIcon(false) != null) {
        baseIcon = createLayeredIcon(((ItemPresentation)element).getIcon(false), elementFlags);
    }
    else if (element instanceof PsiPackage) {
      baseIcon = createLayeredIcon(Icons.PACKAGE_ICON, elementFlags);
    }
    else if (element instanceof PsiFile) {
      PsiFile file = (PsiFile)element;

      VirtualFile virtualFile = file.getVirtualFile();
      final Icon fileTypeIcon;
      if (virtualFile == null) {
        fileTypeIcon = file.getFileType().getIcon();
      }
      else {
        fileTypeIcon = IconUtil.getIcon(virtualFile, flags & ~ICON_FLAG_READ_STATUS, file.getProject());
        if (fileTypeIcon instanceof RowIcon) {
          visibilityAdded = true;
        }
      }
      baseIcon = createLayeredIcon(fileTypeIcon, elementFlags);
    }
    else if (element instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)element;
      Icon symbolIcon = ElementPresentationUtil.getClassBaseIcon(aClass);
      baseIcon = createLayeredIcon(symbolIcon, ElementPresentationUtil.getFlags(aClass, isLocked));
    }
    else {
      return null;
    }
    return visibilityAdded ? baseIcon : addVisibilityIcon(element, flags, baseIcon);
  }

  protected static Icon addVisibilityIcon(final PsiElement element, final int flags, final RowIcon baseIcon) {
    if ((flags & ICON_FLAG_VISIBILITY) != 0) {
      PsiModifierList modifierList = element instanceof PsiModifierListOwner ? ((PsiModifierListOwner)element).getModifierList() : null;
      VisibilityIcons.setVisibilityIcon(modifierList, baseIcon);
    }
    return baseIcon;
  }

  public static RowIcon createLayeredIcon(Icon icon, int flags) {
    if (flags != 0) {
      List<Icon> iconLayers = new SmartList<Icon>();
      for(IconLayer l: ourIconLayers) {
        if ((flags & l.flagMask) != 0) {
          iconLayers.add(l.icon);
        }
      }
      LayeredIcon layeredIcon = new LayeredIcon(1 + iconLayers.size());
      layeredIcon.setIcon(icon, 0);
      for (int i = 0; i < iconLayers.size(); i++) {
        Icon icon1 = iconLayers.get(i);
        layeredIcon.setIcon(icon1, i+1);
      }
      icon = layeredIcon;
    }
    RowIcon baseIcon = new RowIcon(2);
    baseIcon.setIcon(icon, 0);
    return baseIcon;
  }

  private static class IconLayer {
    int flagMask;
    Icon icon;

    IconLayer(final int flagMask, final Icon icon) {
      this.flagMask = flagMask;
      this.icon = icon;
    }
  }

  private static final List<IconLayer> ourIconLayers = new ArrayList<IconLayer>();

  public static void registerIconLayer(int flagMask, Icon icon) {
    for(IconLayer iconLayer: ourIconLayers) {
      if (iconLayer.flagMask == flagMask) return;
    }
    ourIconLayers.add(new IconLayer(flagMask, icon));
  }

  static {
    registerIconLayer(FLAGS_LOCKED, Icons.LOCKED_ICON);
  }
}