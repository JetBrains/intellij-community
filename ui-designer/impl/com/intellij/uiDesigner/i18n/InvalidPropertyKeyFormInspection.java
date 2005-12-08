package com.intellij.uiDesigner.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionProfile;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.lw.StringDescriptor;
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
public class InvalidPropertyKeyFormInspection implements FormInspectionTool, FileCheckingInspection {
  private static BorderProperty myBorderProperty = new BorderProperty();

  @Nullable
  public ErrorInfo[] checkComponent(GuiEditor editor, RadComponent component) {
    final Palette palette = Palette.getInstance(editor.getProject());
    IntrospectedProperty[] props = palette.getIntrospectedProperties(component.getComponentClass());
    List<ErrorInfo> result = null;
    for(IntrospectedProperty prop: props) {
      if (component.isMarkedAsModified(prop) && prop instanceof IntroStringProperty) {
        StringDescriptor descriptor = (StringDescriptor) prop.getValue(component);
        if (descriptor != null) {
          ErrorInfo errInfo = checkDescriptor(prop.getName(), descriptor, editor.getModule());
          if (errInfo != null) {
            if (result == null) {
               result = new ArrayList<ErrorInfo>();
            }
            result.add(errInfo);
          }
        }
      }
    }

    if (component instanceof RadContainer) {
      RadContainer container = (RadContainer) component;
      StringDescriptor descriptor = container.getBorderTitle();
      if (descriptor != null) {
        ErrorInfo errInfo = checkDescriptor(myBorderProperty.getName(), descriptor, editor.getModule());
        if (errInfo != null) {
          if (result == null) {
             result = new ArrayList<ErrorInfo>();
          }
          result.add(errInfo);
        }
      }
    }

    if (component.getParent() instanceof RadTabbedPane) {
      RadTabbedPane parentTabbedPane = (RadTabbedPane) component.getParent();
      final StringDescriptor descriptor = parentTabbedPane.getChildTitle(component);
      if (descriptor != null) {
        ErrorInfo errInfo = checkDescriptor(null, descriptor, editor.getModule());
        if (errInfo != null) {
          if (result == null) {
             result = new ArrayList<ErrorInfo>();
          }
          result.add(errInfo);
        }
      }
    }
    return result == null ? null : result.toArray(new ErrorInfo[result.size()]);
  }

  public boolean isActive(PsiElement psiRoot) {
    final InspectionProfile profile = InspectionProjectProfileManager.getInstance(psiRoot.getProject()).getProfile(psiRoot);
    HighlightDisplayKey key = HighlightDisplayKey.find("UnresolvedPropertyKey");
    if (key == null) {
      return false;
    }
    return profile.isToolEnabled(key);
  }

  @Nullable
  private ErrorInfo checkDescriptor(final String propName, final StringDescriptor descriptor, final Module module) {
    final String bundleName = descriptor.getBundleName();
    final String key = descriptor.getKey();
    if (bundleName == null && key == null) return null;
    if (bundleName == null) {
      return new ErrorInfo(propName,
                           CodeInsightBundle.message("inspection.invalid.property.in.form.quickfix.error.bundle.not.specified"),
                           QuickFix.EMPTY_ARRAY);
    }

    if (key == null) {
      return new ErrorInfo(propName,
                           CodeInsightBundle.message("inspection.invalid.property.in.form.quickfix.error.property.key.not.specified"),
                           QuickFix.EMPTY_ARRAY);
    }


    PropertiesFile bundle = PropertiesUtil.getPropertiesFile(bundleName, module);
    if (bundle == null) {
      return new ErrorInfo(propName,
                           CodeInsightBundle.message("inspection.invalid.property.in.form.quickfix.error.bundle.not.found", bundle),
                           QuickFix.EMPTY_ARRAY);
    }


    final Property property = bundle.findPropertyByKey(key);
    if (property == null) {
      return new ErrorInfo(propName,
                           CodeInsightBundle.message("inspection.invalid.property.in.form.quickfix.error.key.not.found", key, bundleName),
                           QuickFix.EMPTY_ARRAY);
    }
    return null;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(final PsiFile file, final InspectionManager manager, boolean isOnTheFly) {
    if (file.getFileType().equals(StdFileTypes.GUI_DESIGNER_FORM)) {
      final Module module = ModuleUtil.getModuleForFile(file.getProject(), file.getVirtualFile());
      if (module == null) {
        return null;
      }
      final LwRootContainer rootContainer;
      try {
        rootContainer = Utils.getRootContainer(file.getText(), new PsiPropertiesProvider(module));
      }
      catch (Exception e) {
        return null;
      }

      final List<ProblemDescriptor> problems = new ArrayList<ProblemDescriptor>();
      FormEditingUtil.iterateStringDescriptors(rootContainer, new FormEditingUtil.StringDescriptorVisitor<LwComponent>() {
        public boolean visit(final LwComponent component, final StringDescriptor descriptor) {
          final ErrorInfo errorInfo = checkDescriptor(null, descriptor, module);
          if (errorInfo != null) {
            problems.add(manager.createProblemDescriptor(file, errorInfo.myDescription,
                                                         (LocalQuickFix) null,
                                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
          return true;
        }
      });
      if (problems.size() > 0) {
        return problems.toArray(new ProblemDescriptor [problems.size()]);
      }
    }
    return null;
  }
}
