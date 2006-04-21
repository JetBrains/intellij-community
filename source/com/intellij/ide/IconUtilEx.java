package com.intellij.ide;

import com.intellij.util.ui.EmptyIcon;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.RowIcon;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.Icons;
import com.intellij.javaee.model.common.ejb.EnterpriseBean;
import com.intellij.javaee.model.common.ejb.CmpField;
import com.intellij.javaee.model.common.ejb.CmrField;

import javax.swing.*;
import java.awt.*;
import java.awt.image.PixelGrabber;
import java.awt.image.BufferedImage;

public class IconUtilEx {
  public static Icon getEmptyIcon(boolean showVisibility) {
    RowIcon baseIcon = new RowIcon(2);
    EmptyIcon emptyIcon = Icons.CLASS_ICON != null
                          ? new EmptyIcon(Icons.CLASS_ICON.getIconWidth(), Icons.CLASS_ICON.getIconHeight())
                          : null;
    baseIcon.setIcon(emptyIcon, 0);
    if (showVisibility) {
      emptyIcon = Icons.PUBLIC_ICON != null ? new EmptyIcon(Icons.PUBLIC_ICON.getIconWidth(), Icons.PUBLIC_ICON.getIconHeight()) : null;
      baseIcon.setIcon(emptyIcon, 1);
    }
    return baseIcon;
  }

  public static Icon getIcon(Object object, int flags, Project project) {
    if (object instanceof PsiElement) {
      PsiElement element = (PsiElement)object;
      return element.getIcon(flags);
    }
    else if (object instanceof VirtualFile) {
      VirtualFile file = (VirtualFile)object;
      return IconUtil.getIcon(file, flags, project);
    }
    else if (object instanceof Module) {
      return getIcon((Module)object, flags);
    }
    else if (object instanceof EnterpriseBean) {
      return Icons.EJB_ICON;
    }
    else if (object instanceof CmpField) {
      return Icons.EJB_CMP_FIELD_ICON;
    }
    else if (object instanceof CmrField) {
      return Icons.EJB_CMP_FIELD_ICON;
    }
    else {
      throw new IllegalArgumentException("Wrong object type");
    }
  }

  public static void setVisibilityIcon(PsiModifierList modifierList, RowIcon baseIcon) {
    if (modifierList != null) {
      if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
        setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PUBLIC, baseIcon);
      }
      else if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
        setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PRIVATE, baseIcon);
      }
      else if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
        setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PROTECTED, baseIcon);
      }
      else if (modifierList.hasModifierProperty(PsiModifier.PACKAGE_LOCAL)) {
        setVisibilityIcon(PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL, baseIcon);
      }
      else {
        Icon emptyIcon = new EmptyIcon(Icons.PUBLIC_ICON.getIconWidth(), Icons.PUBLIC_ICON.getIconHeight());
        baseIcon.setIcon(emptyIcon, 1);
      }
    }
    else {
      if (Icons.PUBLIC_ICON != null) {
        Icon emptyIcon = new EmptyIcon(Icons.PUBLIC_ICON.getIconWidth(), Icons.PUBLIC_ICON.getIconHeight());
        baseIcon.setIcon(emptyIcon, 1);
      }
    }
  }

  public static void setVisibilityIcon(int accessLevel, RowIcon baseIcon) {
    Icon icon;
    switch (accessLevel) {
      case PsiUtil.ACCESS_LEVEL_PUBLIC:
        icon = Icons.PUBLIC_ICON;
        break;
      case PsiUtil.ACCESS_LEVEL_PROTECTED:
        icon = Icons.PROTECTED_ICON;
        break;
      case PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL:
        icon = Icons.PACKAGE_LOCAL_ICON;
        break;
      case PsiUtil.ACCESS_LEVEL_PRIVATE:
        icon = Icons.PRIVATE_ICON;
        break;
      default:
        if (Icons.PUBLIC_ICON != null) {
          icon = new EmptyIcon(Icons.PUBLIC_ICON.getIconWidth(), Icons.PUBLIC_ICON.getIconHeight());
        }
        else {
          return;
        }
    }
    baseIcon.setIcon(icon, 1);
  }

  public static Icon getIcon(Module module, int flags) {
    return getModuleTypeIcon(module.getModuleType(), flags);
  }

  public static Icon getModuleTypeIcon(final ModuleType moduleType, int flags) {
    return moduleType.getNodeIcon((flags & Iconable.ICON_FLAG_OPEN) != 0);
  }


  public static Icon createLayeredIcon(final Icon backgroundIcon, final Icon foregroundIcon) {
    final LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(backgroundIcon, 0);
    layeredIcon.setIcon(foregroundIcon, 1);
    return layeredIcon;
  }                                 

  public static Icon redden(Icon icon) {
    if (icon instanceof ImageIcon) {
      Image image = ((ImageIcon)icon).getImage();

      PixelGrabber grabber = new PixelGrabber(image, 0, 0, -1, -1, true);
      try {
        grabber.grabPixels();
      }
      catch (InterruptedException e) {
        //
      }
      int[] pixels = (int[])grabber.getPixels();
      System.arraycopy(pixels, 0, pixels=new int[pixels.length],0,pixels.length);
      for (int i = 0; i < pixels.length; i++) {
        int pixel = pixels[i];
        int alpha = (pixel >> 24) & 0xff;
        if (alpha == 0) continue;
        int red = (pixel >> 16) & 0xFF;
        red = 0xff;
        int green = (pixel >> 8) & 0xFF;
        int blue = (pixel >> 0) & 0xFF;
        int value;
        value = ((alpha & 0xFF) << 24) | ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | ((blue & 0xFF) << 0);
        //float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        //value = ((alpha & 0xFF) << 24) | Color.HSBtoRGB(hsb[0], Math.min(1,hsb[1]*1.4f), hsb[2]);
        pixels[i] = value;
      }

      final BufferedImage buffered = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
      buffered.setRGB(0,0,icon.getIconWidth(),icon.getIconHeight(), pixels, 0,icon.getIconWidth());
      image = buffered;
      //image = buffered.getScaledInstance(icon.getIconWidth(), icon.getIconHeight(), Image.SCALE_SMOOTH);

      icon = IconLoader.getIcon(image);
      return icon;
    }
    else if (icon instanceof RowIcon) {
      RowIcon rowIcon = new RowIcon(((RowIcon)icon).getIconCount());
      for (int i = 0; i < ((RowIcon)icon).getIconCount(); i++) {
        Icon subIcon = ((RowIcon)icon).getIcon(i);
        Icon reddened = redden(subIcon);
        rowIcon.setIcon(reddened, i);
      }
      return rowIcon;
    }
    else if (icon instanceof LayeredIcon){
      Icon firstIcon = ((LayeredIcon)icon).getIcons()[0];
      Icon reddened = redden(firstIcon);
      return ((LayeredIcon)icon).replaceFirstIcon(reddened);
    }
    else {
      return null;
    }
  }
}