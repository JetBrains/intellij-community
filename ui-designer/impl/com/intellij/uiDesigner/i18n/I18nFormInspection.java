package com.intellij.uiDesigner.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.i18n.I18nInspection;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.uiDesigner.RadComponent;
import com.intellij.uiDesigner.RadContainer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.inspections.BaseFormInspection;
import com.intellij.uiDesigner.inspections.EditorQuickFixProvider;
import com.intellij.uiDesigner.inspections.FormErrorCollector;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.properties.BorderProperty;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class I18nFormInspection extends BaseFormInspection {
  private static BorderProperty myBorderProperty = new BorderProperty();

  public I18nFormInspection() {
    super("HardCodedStringLiteral");
  }

  protected void checkComponentProperties(final Module module, final IComponent component, final FormErrorCollector collector) {
    for(final IProperty prop: component.getModifiedProperties()) {
      Object propValue = prop.getPropertyValue(component);
      if (propValue instanceof StringDescriptor) {
        StringDescriptor descriptor = (StringDescriptor) propValue;
        if (descriptor != null && isHardCodedStringDescriptor(descriptor)) {
          if (isSetterNonNls(module.getProject(),
                             GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module),
                             component.getComponentClassName(), prop.getName())) {
            continue;
          }
          collector.addError(prop,
                             CodeInsightBundle.message("inspection.i18n.message.in.form",
                                                       JDOMUtil.escapeText(descriptor.getValue())),
                             new EditorQuickFixProvider() {
                               public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
                                 return new I18nizeFormPropertyQuickFix(editor, CodeInsightBundle.message("inspection.i18n.quickfix"),
                                                                        component, (IntrospectedProperty)prop);
                               }
                             });
        }
      }
    }

    if (component instanceof IContainer) {
      final IContainer container = (IContainer) component;
      StringDescriptor descriptor = container.getBorderTitle();
      if (descriptor != null && isHardCodedStringDescriptor(descriptor)) {
        collector.addError(myBorderProperty,
                           CodeInsightBundle.message("inspection.i18n.message.in.form",
                                                     JDOMUtil.escapeText(descriptor.getValue())),
                           new EditorQuickFixProvider() {
                             public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
                               return new I18nizeFormBorderQuickFix(editor, CodeInsightBundle.message("inspection.i18n.quickfix"), (RadContainer)container);
                             }
                           });
      }
    }

    if (component.getParent() instanceof ITabbedPane) {
      ITabbedPane parentTabbedPane = (ITabbedPane) component.getParent();
      final StringDescriptor descriptor = parentTabbedPane.getTabTitle(component);
      if (descriptor != null && isHardCodedStringDescriptor(descriptor)) {
        collector.addError(null,
                           CodeInsightBundle.message("inspection.i18n.message.in.form",
                                                     JDOMUtil.escapeText(descriptor.getValue())),
                           new EditorQuickFixProvider() {
                             public QuickFix createQuickFix(GuiEditor editor, RadComponent component) {
                               return new I18nizeTabTitleQuickFix(editor, CodeInsightBundle.message("inspection.i18n.quickfix"), component);
                             }
                           });
      }
    }
  }

  private boolean isHardCodedStringDescriptor(final StringDescriptor descriptor) {
    if (descriptor.isNoI18n()) {
      return false;
    }
    return descriptor.getBundleName() == null &&
           descriptor.getKey() == null &&
           StringUtil.containsAlphaCharacters(descriptor.getValue());
  }

  private static boolean isSetterNonNls(final Project project, final GlobalSearchScope searchScope,
                                        final String componentClassName, final String propertyName) {
    PsiClass componentClass = PsiManager.getInstance(project).findClass(componentClassName, searchScope);
    if (componentClass == null) {
      return false;
    }
    PsiMethod setter = PropertyUtil.findPropertySetter(componentClass, propertyName, false, true);
    if (setter != null) {
      PsiParameter[] parameters = setter.getParameterList().getParameters();
      if (parameters.length == 1 &&
          "java.lang.String".equals(parameters[0].getType().getCanonicalText()) &&
          AnnotationUtil.isAnnotated(parameters [0], AnnotationUtil.NON_NLS, false)) {
        return true;
      }
    }

    return false;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
    if (file.getFileType().equals(StdFileTypes.GUI_DESIGNER_FORM)) {
      final PsiDirectory directory = file.getContainingDirectory();
      if (directory != null && I18nInspection.isPackageNonNls(directory.getPackage())) {
        return null;
      }
    }

    return super.checkFile(file, manager, isOnTheFly);
  }
}
