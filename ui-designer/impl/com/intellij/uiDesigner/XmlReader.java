package com.intellij.uiDesigner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.uiDesigner.compiler.CodeGenerationException;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.make.PsiNestedFormLoader;
import com.intellij.uiDesigner.palette.Palette;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.radComponents.*;
import com.intellij.uiDesigner.shared.XYLayoutManager;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class XmlReader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.XmlReader");

  private XmlReader() {
  }

  @NotNull
  public static RadRootContainer createRoot(final Module module, final LwRootContainer lwRootContainer, final ClassLoader loader) throws Exception{
    return (RadRootContainer)createComponent(module, lwRootContainer, loader);
  }

  @NotNull
  public static RadComponent createComponent(@NotNull final Module module,
                                             @NotNull final LwComponent lwComponent,
                                             @NotNull final ClassLoader loader) throws Exception{
    // Id
    final String id = lwComponent.getId();
    final RadComponent component;
    Class componentClass = null;

    if (lwComponent instanceof LwNestedForm) {
      LwNestedForm nestedForm = (LwNestedForm) lwComponent;
      boolean recursiveNesting = false;
      try {
        Utils.validateNestedFormLoop(nestedForm.getFormFileName(), new PsiNestedFormLoader(module));
      }
      catch(CodeGenerationException ex) {
        recursiveNesting = true;
      }
      if (recursiveNesting) {
        component = RadErrorComponent.create(
          module,
          id,
          lwComponent.getComponentClassName(),
          lwComponent.getErrorComponentProperties(),
          "Form cannot be loaded because of recursive form nesting");
      }
      else {
        component = new RadNestedForm(module, nestedForm.getFormFileName(), id);
      }
    }
    else {
      if (lwComponent.getErrorComponentProperties() == null) {
        componentClass = Class.forName(lwComponent.getComponentClassName(), true, loader);
      }

      if (lwComponent instanceof LwHSpacer) {
        component = new RadHSpacer(module, id);
      }
      else if (lwComponent instanceof LwVSpacer) {
        component = new RadVSpacer(module, id);
      }
      else if (lwComponent instanceof LwAtomicComponent) {
        if (componentClass == null) {
          component = createErrorComponent(module, id, lwComponent, loader);
        }
        else {
          RadComponent component1;
          try {
            component1 = new RadAtomicComponent(module, componentClass, id);
          }
          catch (final Exception exc) {
            String errorDescription = MessageFormat.format(UIDesignerBundle.message("error.class.cannot.be.instantiated"), lwComponent.getComponentClassName());
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
      }
      else if (lwComponent instanceof LwScrollPane) {
        component = new RadScrollPane(module, id);
      }
      else if (lwComponent instanceof LwTabbedPane) {
        component = new RadTabbedPane(module, id);
      }
      else if (lwComponent instanceof LwSplitPane) {
        component = new RadSplitPane(module, id);
      }
      else if (lwComponent instanceof LwToolBar) {
        component = new RadToolBar(module, id);
      }
      else if (lwComponent instanceof LwContainer) {
        final LwContainer lwContainer = (LwContainer)lwComponent;
        LayoutManager layout = lwContainer.getLayout();
        if (layout instanceof XYLayoutManager) {
          // replace stub layout with the real one
          final XYLayoutManagerImpl xyLayoutManager = new XYLayoutManagerImpl();
          layout = xyLayoutManager;
          xyLayoutManager.setPreferredSize(lwComponent.getBounds().getSize());
        }
        if (componentClass == null) {
          component = createErrorComponent(module, id, lwComponent, loader);
        }
        else {
          if (lwContainer instanceof LwRootContainer) {
            component = new RadRootContainer(module, id);
          }
          else {
            component = new RadContainer(module, componentClass, id);

            String layoutManagerName = lwContainer.getLayoutManager();
            if (layoutManagerName == null || layoutManagerName.length() == 0) {
              if (layout instanceof XYLayoutManager) {
                layoutManagerName = UIFormXmlConstants.LAYOUT_XY;
              }
              else {
                layoutManagerName = UIFormXmlConstants.LAYOUT_INTELLIJ;
              }
            }
            ((RadContainer) component).setLayoutManager(RadLayoutManager.createLayoutManager(layoutManagerName));
          }
          ((RadContainer)component).setLayout(layout);
        }
      }
      else {
        throw new IllegalArgumentException("unexpected component: " + lwComponent);
      }
    }

    // binding
    component.setBinding(lwComponent.getBinding());
    component.setCustomCreate(lwComponent.isCustomCreate());

    // bounds
    component.setBounds(lwComponent.getBounds());

    // properties
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

    if (component instanceof RadContainer) {
      final RadContainer container = (RadContainer)component;
      //noinspection ConstantConditions
      final LwContainer lwContainer = (LwContainer)lwComponent;

      copyBorder(container, lwContainer);

      // add children
      for (int i=0; i < lwContainer.getComponentCount(); i++){
        container.addComponent(createComponent(module, (LwComponent)lwContainer.getComponent(i), loader));
      }
    }

    if (component instanceof RadRootContainer) {
      final RadRootContainer radRootContainer = (RadRootContainer)component;
      //noinspection ConstantConditions
      final LwRootContainer lwRootContainer = (LwRootContainer)lwComponent;
      radRootContainer.setClassToBind(lwRootContainer.getClassToBind());
      radRootContainer.setMainComponentBinding(lwRootContainer.getMainComponentBinding());
      radRootContainer.setButtonGroups(lwRootContainer.getButtonGroups());
      radRootContainer.getDelegee().setBackground(Color.WHITE);
    }

    component.doneLoadingFromLw();
    return component;
  }

  private static void copyBorder(final RadContainer container, final LwContainer lwContainer) {
    container.setBorderType(lwContainer.getBorderType());
    container.setBorderTitle(lwContainer.getBorderTitle());
    container.setBorderTitleJustification(lwContainer.getBorderTitleJustification());
    container.setBorderTitlePosition(lwContainer.getBorderTitlePosition());
    container.setBorderTitleFont(lwContainer.getBorderTitleFont());
    container.setBorderTitleColor(lwContainer.getBorderTitleColor());
  }

  private static RadErrorComponent createErrorComponent(final Module module, final String id, final LwComponent lwComponent, final ClassLoader loader) {
    final String componentClassName = lwComponent.getComponentClassName();
    final String errorDescription = Utils.validateJComponentClass(loader, componentClassName, true);
    return RadErrorComponent.create(
      module,
      id,
      lwComponent.getComponentClassName(),
      lwComponent.getErrorComponentProperties(),
      errorDescription != null? errorDescription : UIDesignerBundle.message("error.cannot.load.class", componentClassName)
    );
  }

}
