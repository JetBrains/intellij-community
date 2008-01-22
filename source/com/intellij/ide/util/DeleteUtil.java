package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;

import java.text.MessageFormat;

/**
 * @author dsl
 */
public class DeleteUtil {
  private DeleteUtil() {}

  public static void appendMessage(int count, @NonNls String propertyKey, StringBuffer buffer) {
    if (count > 0) {
      if (buffer.length() > 0) {
        buffer.append(" ").append(IdeBundle.message("prompt.delete.and")).append(" ");
      }
      buffer.append(count);
      buffer.append(' ');
      buffer.append(IdeBundle.message(propertyKey, count));
    }
  }

  public static String generateWarningMessage(String messageTemplate, final PsiElement[] elements) {
    int methods = 0;
    int fields = 0;
    int interfaces = 0;
    int classes = 0;
    int files = 0;
    int directories = 0;
    int packages = 0;
    int packageDirectories = 0;
    int customElements = 0;
    int properties = 0;
    String[] objName = new String[] { "", "", "" };

    for (final PsiElement elementToDelete : elements) {
      if (elementToDelete instanceof Property) {
        objName[0] = ((Property)elementToDelete).getName();
        objName[1] = IdeBundle.message("prompt.delete.property", 1);
        properties++;
      }
      else if (elementToDelete instanceof PsiMethod) {
        objName[0] = ((PsiMethod)elementToDelete).getName();
        objName[1] = IdeBundle.message("prompt.delete.method", 1);
        methods++;
      }
      else if (elementToDelete instanceof PsiField) {
        objName[0] = ((PsiField)elementToDelete).getName();
        objName[1] = IdeBundle.message("prompt.delete.field", 1);
        fields++;
      }
      else if (elementToDelete instanceof PsiClass) {
        objName[0] = ((PsiClass)elementToDelete).getName();
        if (((PsiClass)elementToDelete).isInterface()) {
          objName[1] = IdeBundle.message("prompt.delete.interface", 1);
          interfaces++;
        }
        else {
          objName[1] = elementToDelete instanceof PsiTypeParameter ? IdeBundle.message("prompt.delete.type.parameter", 1) : IdeBundle.message("prompt.delete.class", 1);
          classes++;
        }
      }
      else if (elementToDelete instanceof PsiFile) {
        objName[0] = ((PsiFile)elementToDelete).getName();
        objName[1] = IdeBundle.message("prompt.delete.file", 1);
        files++;
      }
      else if (elementToDelete instanceof PsiDirectory) {
        directories = processDirectory(elementToDelete, objName, directories);
      }
      else if (elementToDelete instanceof PsiPackage) {
        final PsiPackage psiPackage = (PsiPackage)elementToDelete;
        final String name = psiPackage.getName();
        final PsiDirectory[] psiDirectories = psiPackage.getDirectories();
        final int count = psiDirectories.length;
        objName[1] = IdeBundle.message("prompt.delete.package", 1);
        objName[0] = name;
        objName[2] = " " + buildDirectoryMessage(count);
        packages += 1;
        packageDirectories += count;
      }
      else if (elementToDelete instanceof PsiNamedElement) {
        objName[0] = ((PsiNamedElement) elementToDelete).getName();
        objName[1] = LanguageFindUsages.INSTANCE.forLanguage(elementToDelete.getLanguage()).getType(elementToDelete);
        customElements++;
      }
    }

    String warningMessage;
    if (elements.length == 1) {
      warningMessage = MessageFormat.format(messageTemplate, objName[1] + " \"" + objName[0] + "\"");
    }
    else {
      StringBuffer buffer = new StringBuffer();
      appendMessage(directories, "prompt.delete.directory", buffer);
      appendMessage(files, "prompt.delete.file", buffer);
      appendMessage(classes, "prompt.delete.class", buffer);
      appendMessage(interfaces, "prompt.delete.interface", buffer);
      appendMessage(methods, "prompt.delete.method", buffer);
      appendMessage(fields, "prompt.delete.field", buffer);
      appendMessage(properties, "prompt.delete.property", buffer);
      if (packages > 0) {
        appendMessage(packages, "prompt.delete.package", buffer);
        buffer.append(' ');
        buffer.append(buildDirectoryMessage(packageDirectories));
      }
      appendMessage(customElements, "prompt.delete.element", buffer);
      warningMessage = MessageFormat.format(messageTemplate, buffer.toString());
    }
    return warningMessage;
  }

  private static String buildDirectoryMessage(final int count) {
    return IdeBundle.message("prompt.delete.directory.paren", count);
  }

  private static int processDirectory(final PsiElement elementToDelete, String[] objName, int directories) {
    objName[0] = ((PsiDirectory)elementToDelete).getName();
    objName[1] = IdeBundle.message("prompt.delete.directory", 1);
    directories++;
    return directories;
  }
}
