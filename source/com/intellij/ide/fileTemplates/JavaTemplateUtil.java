package com.intellij.ide.fileTemplates;

import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Properties;

/**
 * @author yole
 */
public class JavaTemplateUtil {
  @NonNls public static final String TEMPLATE_CATCH_BODY = "Catch Statement Body.java";
  @NonNls public static final String TEMPLATE_IMPLEMENTED_METHOD_BODY = "Implemented Method Body.java";
  @NonNls public static final String TEMPLATE_OVERRIDDEN_METHOD_BODY = "Overridden Method Body.java";
  @NonNls public static final String TEMPLATE_FROM_USAGE_METHOD_BODY = "New Method Body.java";
  @NonNls public static final String TEMPLATE_I18NIZED_EXPRESSION = "I18nized Expression.java";
  @NonNls public static final String TEMPLATE_I18NIZED_CONCATENATION = "I18nized Concatenation.java";
  @NonNls public static final String TEMPLATE_I18NIZED_JSP_EXPRESSION = "I18nized JSP Expression.jsp";
  @NonNls public static final String INTERNAL_CLASS_TEMPLATE_NAME = "Class";
  @NonNls public static final String INTERNAL_INTERFACE_TEMPLATE_NAME = "Interface";
  @NonNls public static final String INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME = "AnnotationType";
  @NonNls public static final String INTERNAL_ENUM_TEMPLATE_NAME = "Enum";

  private JavaTemplateUtil() {
  }

  public static void setClassAndMethodNameProperties (Properties properties, PsiClass aClass, PsiMethod method) {
    String className = aClass.getQualifiedName();
    if (className == null) className = "";
    properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, className);

    String methodName = method.getName();
    properties.setProperty(FileTemplate.ATTRIBUTE_METHOD_NAME, methodName);
  }

  public static void setPackageNameAttribute (@NotNull Properties properties, @NotNull PsiDirectory directory) {
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage != null) {
      String packageName = aPackage.getQualifiedName();
      if (packageName.length() > 0) {
        properties.setProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME, packageName);
        return;
      }
    }
    properties.setProperty(FileTemplate.ATTRIBUTE_PACKAGE_NAME, "");
  }
}
