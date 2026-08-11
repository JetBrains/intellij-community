// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.JBColor;
import com.intellij.uiDesigner.compiler.RecursiveFormNestingException;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwAtomicComponent;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import com.intellij.uiDesigner.lw.LwHSpacer;
import com.intellij.uiDesigner.lw.LwIntrospectedProperty;
import com.intellij.uiDesigner.lw.LwNestedForm;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.lw.LwScrollPane;
import com.intellij.uiDesigner.lw.LwSplitPane;
import com.intellij.uiDesigner.lw.LwTabbedPane;
import com.intellij.uiDesigner.lw.LwToolBar;
import com.intellij.uiDesigner.lw.LwVSpacer;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.make.PsiNestedFormLoader;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.radComponents.LayoutManagerRegistry;
import com.intellij.uiDesigner.radComponents.RadAtomicComponent;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;
import com.intellij.uiDesigner.radComponents.RadErrorComponent;
import com.intellij.uiDesigner.radComponents.RadHSpacer;
import com.intellij.uiDesigner.radComponents.RadLayoutManager;
import com.intellij.uiDesigner.radComponents.RadNestedForm;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.uiDesigner.radComponents.RadScrollPane;
import com.intellij.uiDesigner.radComponents.RadSplitPane;
import com.intellij.uiDesigner.radComponents.RadTabbedPane;
import com.intellij.uiDesigner.radComponents.RadTable;
import com.intellij.uiDesigner.radComponents.RadToolBar;
import com.intellij.uiDesigner.radComponents.RadVSpacer;
import com.intellij.uiDesigner.radComponents.XYLayoutManagerImpl;
import com.intellij.uiDesigner.shared.XYLayoutManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTable;
import java.awt.Color;
import java.awt.LayoutManager;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class XmlReader {
  private XmlReader() {
  }

  public static @NotNull RadRootContainer createRoot(final ModuleProvider module, final LwRootContainer lwRootContainer, final ClassLoader loader,
                                                     final Locale stringDescriptorLocale) throws Exception{
    return (RadRootContainer)createComponent(module, lwRootContainer, loader, stringDescriptorLocale);
  }

  public static @NotNull RadComponent createComponent(final @NotNull ModuleProvider module,
                                                      final @NotNull LwComponent lwComponent,
                                                      final @NotNull ClassLoader loader,
                                                      final Locale stringDescriptorLocale) throws Exception{
    return createComponent(module, lwComponent, loader, stringDescriptorLocale, isProjectTrusted(module));
  }

  /**
   * Answering this walks the roots of the project, so it is asked once per form rather than once per component.
   */
  private static boolean isProjectTrusted(final @NotNull ModuleProvider module) {
    Project project = module.getProject();
    return project == null || TrustedProjects.isProjectTrusted(project);
  }

  private static @NotNull RadComponent createComponent(final @NotNull ModuleProvider module,
                                                       final @NotNull LwComponent lwComponent,
                                                       final @NotNull ClassLoader loader,
                                                       final Locale stringDescriptorLocale,
                                                       final boolean projectTrusted) throws Exception{
    // Id
    final String id = lwComponent.getId();
    final RadComponent component;
    Class componentClass = null;

    if (lwComponent instanceof LwNestedForm nestedForm) {
      boolean recursiveNesting = false;
      try {
        Utils.validateNestedFormLoop(nestedForm.getFormFileName(), new PsiNestedFormLoader(module.getModule()));
      }
      catch(RecursiveFormNestingException ex) {
        recursiveNesting = true;
      }
      if (recursiveNesting) {
        component = RadErrorComponent.create(
          module,
          id,
          lwComponent.getComponentClassName(),
          lwComponent.getErrorComponentProperties(), UIDesignerBundle.message("error.recursive.form.nesting"));
      }
      else {
        component = new RadNestedForm(module, nestedForm.getFormFileName(), id);
      }
    }
    else {
      if (lwComponent.getErrorComponentProperties() == null &&
          !isForbiddenByProjectTrust(lwComponent.getComponentClassName(), loader, projectTrusted)) {
        componentClass = Class.forName(lwComponent.getComponentClassName(), true, loader);
      }

      if (lwComponent instanceof LwHSpacer) {
        component = new RadHSpacer(module, id);
      }
      else if (lwComponent instanceof LwVSpacer) {
        component = new RadVSpacer(module, id);
      }
      // every remaining kind of component is created from its class - a container names one just as an atomic
      // component does, and it can be a class of the project too
      else if (componentClass == null) {
        component = createErrorComponent(module, id, lwComponent, loader, projectTrusted);
      }
      else if (lwComponent instanceof LwAtomicComponent) {
        RadComponent component1;
        try {
          if (JTable.class.isAssignableFrom(componentClass)) {
            component1 = new RadTable(module, componentClass, id);
          }
          else {
            component1 = new RadAtomicComponent(module, componentClass, id);
          }
        }
        catch (final Exception exc) {
          String errorDescription = UIDesignerBundle.message("error.class.cannot.be.instantiated", lwComponent.getComponentClassName());
          final String message = FormEditingUtil.getExceptionMessage(exc);
          if (message != null) {
            errorDescription += ": " + message;
          }
          component1 = RadErrorComponent.create(
            module,
            id,
            lwComponent.getComponentClassName(),
            lwComponent.getErrorComponentProperties(),
            errorDescription
          );
        }
        component = component1;
      }
      else if (lwComponent instanceof LwScrollPane) {
        component = new RadScrollPane(module, componentClass, id);
      }
      else if (lwComponent instanceof LwTabbedPane) {
        component = new RadTabbedPane(module, componentClass, id);
      }
      else if (lwComponent instanceof LwSplitPane) {
        component = new RadSplitPane(module, componentClass, id);
      }
      else if (lwComponent instanceof LwToolBar) {
        component = new RadToolBar(module, componentClass, id);
      }
      else if (lwComponent instanceof LwContainer lwContainer) {
        LayoutManager layout = lwContainer.getLayout();
        if (layout instanceof XYLayoutManager) {
          // replace stub layout with the real one
          final XYLayoutManagerImpl xyLayoutManager = new XYLayoutManagerImpl();
          layout = xyLayoutManager;
          xyLayoutManager.setPreferredSize(lwComponent.getBounds().getSize());
        }
        if (lwContainer instanceof LwRootContainer) {
          component = new RadRootContainer(module, id);
          if (stringDescriptorLocale != null) {
            ((RadRootContainer) component).setStringDescriptorLocale(stringDescriptorLocale);
          }
        }
        else {
          component = new RadContainer(module, componentClass, id);

          String layoutManagerName = lwContainer.getLayoutManager();
          if (layoutManagerName == null || layoutManagerName.isEmpty()) {
            if (layout instanceof XYLayoutManager) {
              layoutManagerName = UIFormXmlConstants.LAYOUT_XY;
            }
            else {
              layoutManagerName = UIFormXmlConstants.LAYOUT_INTELLIJ;
            }
          }

          RadLayoutManager layoutManager = LayoutManagerRegistry.createLayoutManager(layoutManagerName);
          RadContainer container = (RadContainer)component;
          layoutManager.readLayout(lwContainer, container);
          container.setLayoutManager(layoutManager);
        }
        ((RadContainer)component).setLayout(layout);
      }
      else {
        throw new IllegalArgumentException("unexpected component: " + lwComponent);
      }
    }

    // binding
    component.setBinding(lwComponent.getBinding());
    component.setCustomCreate(lwComponent.isCustomCreate());
    component.setDefaultBinding(lwComponent.isDefaultBinding());

    // bounds
    component.setBounds(lwComponent.getBounds());

    // properties
    if (stringDescriptorLocale != null) {
      component.putClientProperty(RadComponent.CLIENT_PROP_LOAD_TIME_LOCALE, stringDescriptorLocale);
    }
    final LwIntrospectedProperty[] properties = lwComponent.getAssignedIntrospectedProperties();
    if (componentClass != null) {
      final Palette palette = Palette.getInstance(module.getProject());
      for (final LwIntrospectedProperty lwProperty : properties) {
        final IntrospectedProperty property = palette.getIntrospectedProperty(component, lwProperty.getName());
        if (property == null) {
          continue;
        }
        component.loadLwProperty(lwComponent, lwProperty, property);
      }
    }

    // GridConstraints
    component.getConstraints().restore(lwComponent.getConstraints());

    component.setCustomLayoutConstraints(lwComponent.getCustomLayoutConstraints());

    HashMap clientProps = lwComponent.getDelegeeClientProperties();
    for(Object o: clientProps.entrySet()) {
      Map.Entry entry = (Map.Entry) o;
      Object value = entry.getValue();
      if (value instanceof StringDescriptor) {
        value = ((StringDescriptor) value).getValue();
      }
      component.getDelegee().putClientProperty(entry.getKey(), value);
    }

    if (component instanceof RadContainer container) {
      final LwContainer lwContainer = (LwContainer)lwComponent;

      copyBorder(container, lwContainer);

      // add children
      for (int i=0; i < lwContainer.getComponentCount(); i++){
        container.addComponent(
          createComponent(module, (LwComponent)lwContainer.getComponent(i), loader, stringDescriptorLocale, projectTrusted));
      }
    }

    if (component instanceof RadRootContainer radRootContainer) {
      final LwRootContainer lwRootContainer = (LwRootContainer)lwComponent;
      radRootContainer.setClassToBind(lwRootContainer.getClassToBind());
      radRootContainer.setMainComponentBinding(lwRootContainer.getMainComponentBinding());
      radRootContainer.setButtonGroups(lwRootContainer.getButtonGroups());
      radRootContainer.setInspectionSuppressions(lwRootContainer.getInspectionSuppressions());
      radRootContainer.getDelegee().setBackground(new JBColor(Color.WHITE, UIUtil.getListBackground()));
    }

    component.doneLoadingFromLw();
    component.putClientProperty(RadComponent.CLIENT_PROP_LOAD_TIME_LOCALE, null);
    return component;
  }

  private static void copyBorder(final RadContainer container, final LwContainer lwContainer) {
    container.setBorderType(lwContainer.getBorderType());
    container.setBorderTitle(lwContainer.getBorderTitle());
    container.setBorderTitleJustification(lwContainer.getBorderTitleJustification());
    container.setBorderTitlePosition(lwContainer.getBorderTitlePosition());
    container.setBorderTitleFont(lwContainer.getBorderTitleFont());
    container.setBorderTitleColor(lwContainer.getBorderTitleColor());
    container.setBorderSize(lwContainer.getBorderSize());
    container.setBorderColor(lwContainer.getBorderColor());
  }

  /**
   * Whether creating this component would run code from a project the user has not trusted. Resolving a class is
   * harmless, but the designer goes on to initialize it and to create an instance of it, which runs its static
   * initializer and its constructor - and has the {@code Introspector} instantiate its {@code BeanInfo} on top of that
   * (IDEA-392515). An untrusted project gets a placeholder instead, the same as a class that cannot be resolved.
   */
  private static boolean isForbiddenByProjectTrust(final @NotNull String componentClassName,
                                                   final @NotNull ClassLoader loader,
                                                   final boolean projectTrusted) {
    if (projectTrusted) {
      return false;
    }
    try {
      // resolving without initializing runs nothing, and tells whether the class is the project's at all
      return LoaderFactory.isProjectClass(Class.forName(componentClassName, false, loader));
    }
    catch (ClassNotFoundException | LinkageError e) {
      // it cannot be created regardless of trust, and the message about that is the more useful one
      return false;
    }
  }

  private static RadErrorComponent createErrorComponent(final ModuleProvider module, final String id, final LwComponent lwComponent,
                                                        final ClassLoader loader, final boolean projectTrusted) {
    final String componentClassName = lwComponent.getComponentClassName();
    if (isForbiddenByProjectTrust(componentClassName, loader, projectTrusted)) {
      return RadErrorComponent.create(
        module,
        id,
        componentClassName,
        lwComponent.getErrorComponentProperties(),
        UIDesignerBundle.message("error.project.not.trusted", componentClassName)
      );
    }
    final @NlsSafe String errorDescription = Utils.validateJComponentClass(loader, componentClassName, true);
    return RadErrorComponent.create(
      module,
      id,
      lwComponent.getComponentClassName(),
      lwComponent.getErrorComponentProperties(),
      errorDescription != null? errorDescription : UIDesignerBundle.message("error.cannot.load.class", componentClassName)
    );
  }

}
