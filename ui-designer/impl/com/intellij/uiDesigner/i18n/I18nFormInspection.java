package com.intellij.uiDesigner.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.i18n.I18nInspection;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionProfile;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.properties.BorderProperty;
import com.intellij.uiDesigner.propertyInspector.properties.IntroStringProperty;
import com.intellij.uiDesigner.quickFixes.FormInspectionTool;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class I18nFormInspection implements FormInspectionTool, FileCheckingInspection {
  private static BorderProperty myBorderProperty = new BorderProperty();

  public boolean isActive(PsiElement psiRoot) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(psiRoot.getProject()).getProfile(psiRoot);
    HighlightDisplayKey key = HighlightDisplayKey.find("HardCodedStringLiteral");
    if (key == null) {
      return false;
    }
    return profile.isToolEnabled(key);
  }

  @Nullable
  public ErrorInfo[] checkComponent(GuiEditor editor, RadComponent component) {
    final Palette palette = Palette.getInstance(editor.getProject());
    IntrospectedProperty[] props = palette.getIntrospectedProperties(component.getComponentClass());
    List<ErrorInfo> result = null;
    for(IntrospectedProperty prop: props) {
      if (component.isMarkedAsModified(prop) && prop instanceof IntroStringProperty) {
        StringDescriptor descriptor = (StringDescriptor) prop.getValue(component);
        if (descriptor != null && isHardCodedStringDescriptor(descriptor)) {
          if (isSetterNonNls(editor.getProject(),
                             GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(editor.getModule()),
                             component.getComponentClassName(), prop.getName())) {
            continue;
          }
          if (result == null) {
            result = new ArrayList<ErrorInfo>();
          }
          result.add(new ErrorInfo(prop.getName(), CodeInsightBundle.message("inspection.i18n.message.general"),
                                   new QuickFix[] { new I18nizeFormPropertyQuickFix(editor, CodeInsightBundle.message("inspection.i18n.quickfix"), component, prop) }));
        }
      }
    }

    if (component instanceof RadContainer) {
      RadContainer container = (RadContainer) component;
      StringDescriptor descriptor = container.getBorderTitle();
      if (descriptor != null && isHardCodedStringDescriptor(descriptor)) {
        if (result == null) {
          result = new ArrayList<ErrorInfo>();
        }
        result.add(new ErrorInfo(myBorderProperty.getName(), CodeInsightBundle.message("inspection.i18n.message.general"),
                             new QuickFix[] { new I18nizeFormBorderQuickFix(editor, CodeInsightBundle.message("inspection.i18n.quickfix"), container) }));
      }
    }

    if (component.getParent() instanceof RadTabbedPane) {
      RadTabbedPane parentTabbedPane = (RadTabbedPane) component.getParent();
      final StringDescriptor descriptor = parentTabbedPane.getChildTitle(component);
      if (descriptor != null && isHardCodedStringDescriptor(descriptor)) {
        if (result == null) {
          result = new ArrayList<ErrorInfo>();
        }
        result.add(new ErrorInfo(null, CodeInsightBundle.message("inspection.i18n.message.general"),
                             new QuickFix[] { new I18nizeTabTitleQuickFix(editor, CodeInsightBundle.message("inspection.i18n.quickfix"), component) }));
      }
    }

    return result == null ? null : result.toArray(new ErrorInfo[result.size()]);
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
      final Module module = ModuleUtil.getModuleForFile(file.getProject(), file.getVirtualFile());
      if (module == null) {
        return null;
      }

      final PsiDirectory directory = file.getContainingDirectory();
      if (directory != null && I18nInspection.isPackageNonNls(directory.getPackage())) {
        return null;
      }

      final LwRootContainer rootContainer;
      try {
        rootContainer = Utils.getRootContainer(file.getText(), new PsiPropertiesProvider(module));
      }
      catch (Exception e) {
        return null;
      }

      List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
      checkGuiFormContainer(file, rootContainer, problems);
      if (problems.size() > 0) {
        return problems.toArray(new ProblemDescriptor [problems.size()]);
      }
    }
    return null;
  }

  private void checkGuiFormContainer(final PsiFile file, final LwContainer container, final List<ProblemDescriptor> problems) {
    InspectionManager manager = InspectionManager.getInstance(file.getProject());
    for(int i=0; i<container.getComponentCount(); i++) {
      LwComponent component = (LwComponent)container.getComponent(i);

      LwIntrospectedProperty[] props = component.getAssignedIntrospectedProperties();
      for(LwIntrospectedProperty prop: props) {
        if (prop instanceof LwRbIntroStringProperty) {
          StringDescriptor descriptor = (StringDescriptor) component.getPropertyValue(prop);
          if (isHardCodedStringDescriptor(descriptor)) {
            if (isSetterNonNls(file.getProject(), file.getResolveScope(), component.getComponentClassName(), prop.getName())) {
              continue;
            }

            problems.add(manager.createProblemDescriptor(file,
                                                         CodeInsightBundle.message("inspection.i18n.message.in.form",
                                                                                   JDOMUtil.escapeText(descriptor.getValue())),
                                                         (LocalQuickFix) null,
                                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
      }

      if (container instanceof LwTabbedPane) {
        LwTabbedPane.Constraints constraints = (LwTabbedPane.Constraints) component.getCustomLayoutConstraints();
        if (constraints != null && isHardCodedStringDescriptor(constraints.myTitle)) {
          problems.add(manager.createProblemDescriptor(file, CodeInsightBundle.message("inspection.i18n.message.in.form",
                                                                                       JDOMUtil.escapeText(constraints.myTitle.getValue())),
                                                       (LocalQuickFix) null,
                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
      }

      if (component instanceof LwContainer) {
        final LwContainer childContainer = (LwContainer)component;
        final StringDescriptor borderTitle = childContainer.getBorderTitle();
        if (borderTitle != null && isHardCodedStringDescriptor(borderTitle)) {
          problems.add(manager.createProblemDescriptor(file,
                                                       CodeInsightBundle.message("inspection.i18n.message.in.form",
                                                                                 JDOMUtil.escapeText(borderTitle.getValue())),
                                                       (LocalQuickFix) null,
                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
        }
        checkGuiFormContainer(file, childContainer, problems);
      }
    }
  }
}
