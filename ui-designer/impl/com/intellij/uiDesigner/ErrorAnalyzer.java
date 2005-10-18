package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IContainer;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.quickFixes.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.jetbrains.annotations.NonNls;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ErrorAnalyzer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.ErrorAnalyzer");

  /**
   * Value {@link ErrorInfo}
   */
  @NonNls
  public static final String CLIENT_PROP_CLASS_TO_BIND_ERROR = "classToBindError";
  /**
   * Value {@link ErrorInfo}
   */
  @NonNls
  public static final String CLIENT_PROP_BINDING_ERROR = "bindingError";
  /**
   * Value {@link ErrorInfo}
   */
  @NonNls
  public static final String CLIENT_PROP_GENERAL_ERROR = "generalError";

  public static void analyzeErrors(final GuiEditor editor, final IRootContainer rootContainer){
    analyzeErrors(editor.getModule(), editor.getFile(), editor, rootContainer);
  }

  /**
   * @param editor if null, no quick fixes are created. This is used in form to source compiler.
   */
  public static void analyzeErrors(
    final Module module,
    final VirtualFile formFile,
    final GuiEditor editor,
    final IRootContainer rootContainer
  ){
    LOG.assertTrue(module != null);
    LOG.assertTrue(formFile != null);
    LOG.assertTrue(rootContainer != null);

    // 1. Validate class to bind
    final String classToBind = rootContainer.getClassToBind();
    final PsiClass psiClass;
    if(classToBind != null){
      psiClass = FormEditingUtil.findClassToBind(module, classToBind);
      if(psiClass == null){
        final QuickFix[] fixes = editor != null ? new QuickFix[]{new CreateClassToBindFix(editor, classToBind)} : QuickFix.EMPTY_ARRAY;
        final ErrorInfo errorInfo = new ErrorInfo(UIDesignerBundle.message("error.class.does.not.exist", classToBind),
                                                  fixes);
        rootContainer.putClientProperty(CLIENT_PROP_CLASS_TO_BIND_ERROR, errorInfo);
      }
      else{
        rootContainer.putClientProperty(CLIENT_PROP_CLASS_TO_BIND_ERROR, null);
      }
    }
    else{
      psiClass = null;
    }

    // 2. Validate bindings to fields
    // field name -> error message
    final ArrayList<String> usedBindings = new ArrayList<String>(); // for performance reasons
    final HashMap<String, PsiType> className2Type = new HashMap<String,PsiType>(); // for performance reasons
    FormEditingUtil.iterate(
      rootContainer,
      new FormEditingUtil.ComponentVisitor<IComponent>() {
        public boolean visit(final IComponent component) {
          // Reset previous error (if any)
          component.putClientProperty(CLIENT_PROP_BINDING_ERROR, null);

          final String binding = component.getBinding();
          if(binding == null){
            return true;
          }

          // a. Check that field exists and field is not static
          if(psiClass != null){
            final PsiField[] fields = psiClass.getFields();
            PsiField field = null;
            for(int i = fields.length - 1; i >=0 ; i--){
              if(binding.equals(fields[i].getName())){
                field = fields[i];
                break;
              }
            }
            if(field == null){
              final QuickFix[] fixes = editor != null ? new QuickFix[]{
                new CreateFieldFix(editor, psiClass, component.getComponentClassName(), binding)} :
                                                                                                  QuickFix.EMPTY_ARRAY;
              component.putClientProperty(
               CLIENT_PROP_BINDING_ERROR,
               new ErrorInfo(
                 UIDesignerBundle.message("error.no.field.in.class", binding, classToBind),
                 fixes
               )
              );
              return true;
            }
            else if(field.hasModifierProperty(PsiModifier.STATIC)){
              component.putClientProperty(
                CLIENT_PROP_BINDING_ERROR,
                new ErrorInfo(
                  UIDesignerBundle.message("error.cant.bind.to.static", binding),
                  QuickFix.EMPTY_ARRAY
                )
              );
              return true;
            }

            // Check that field has correct fieldType
            try {
              final PsiType componentType;
              final String className = component.getComponentClassName().replace('$', '.'); // workaround for PSI
              if(className2Type.containsKey(className)){
                componentType = className2Type.get(className);
              }
              else{
                componentType = PsiManager.getInstance(module.getProject()).getElementFactory().createTypeFromText(
                  className,
                  null
                );
              }
              final PsiType fieldType = field.getType();
              if(fieldType != null && componentType != null && !fieldType.isAssignableFrom(componentType)){
                final QuickFix[] fixes = editor != null ? new QuickFix[]{
                  new ChangeFieldTypeFix(editor, field, componentType)
                } : QuickFix.EMPTY_ARRAY;
                component.putClientProperty(
                  CLIENT_PROP_BINDING_ERROR,
                  new ErrorInfo(
                    UIDesignerBundle.message("error.bind.incompatible.types", fieldType.getPresentableText(), className),
                    fixes
                  )
                );
              }
            }
            catch (IncorrectOperationException e) {
            }
          }

          // b. Check that binding is unique
          if(usedBindings.contains(binding)){
            // TODO[vova] implement
            component.putClientProperty(
              CLIENT_PROP_BINDING_ERROR,
              new ErrorInfo(
                UIDesignerBundle.message("error.binding.already.exists", binding),
                QuickFix.EMPTY_ARRAY
              )
            );
            return true;
          }

          usedBindings.add(binding);

          return true;
        }
      }
    );

    // Check that there are no panels in XY with children
    FormEditingUtil.iterate(
      rootContainer,
      new FormEditingUtil.ComponentVisitor<IComponent>() {
        public boolean visit(final IComponent component) {
          // Clear previous error (if any)
          component.putClientProperty(CLIENT_PROP_GENERAL_ERROR, null);

          if(!(component instanceof IContainer)){
            return true;
          }

          final IContainer container = (IContainer)component;
          if(container instanceof IRootContainer){
            final IRootContainer rootContainer = (IRootContainer)container;
            if(rootContainer.getComponentCount() > 1){
              // TODO[vova] implement
              component.putClientProperty(
                CLIENT_PROP_GENERAL_ERROR,
                new ErrorInfo(
                  UIDesignerBundle.message("error.multiple.toplevel.components"),
                  QuickFix.EMPTY_ARRAY
                )
              );
            }
          }
          else if(container.isXY() && container.getComponentCount() > 0){
            // TODO[vova] implement
            component.putClientProperty(
              CLIENT_PROP_GENERAL_ERROR,
              new ErrorInfo(
                UIDesignerBundle.message("error.panel.not.laid.out"),
                QuickFix.EMPTY_ARRAY
              )
            );
          }
          return true;
        }
      }
    );

    try {
      // Run inspections for form elements
      final PsiFile formPsiFile = PsiManager.getInstance(module.getProject()).findFile(formFile);
      if (formPsiFile != null) {
        final InspectionProfileImpl profile = DaemonCodeAnalyzerSettings.getInstance().getInspectionProfile(formPsiFile);
        final List<FormInspectionTool> formInspectionTools = new ArrayList<FormInspectionTool>();
        for(LocalInspectionTool tool: profile.getHighlightingLocalInspectionTools()) {
          if (tool instanceof FormInspectionTool) {
            formInspectionTools.add((FormInspectionTool) tool);
          }
        }

        if (formInspectionTools.size() > 0) {
          FormEditingUtil.iterate(
            rootContainer,
            new FormEditingUtil.ComponentVisitor<RadComponent>() {
              public boolean visit(final RadComponent component) {
                if (component.getClientProperty(CLIENT_PROP_GENERAL_ERROR) == null) {
                  for(FormInspectionTool tool: formInspectionTools) {
                    ErrorInfo errorInfo = tool.checkComponent(editor, component);
                    if (errorInfo != null) {
                      component.putClientProperty(CLIENT_PROP_GENERAL_ERROR, errorInfo);
                      break;
                    }
                  }
                }
                return true;
              }
            }
          );
        }
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  /**
   * @return first ErrorInfo for the specified component. If component doesn't contain
   * any error then the method returns <code>null</code>.
   */
  public static ErrorInfo getErrorForComponent(final IComponent component){
    LOG.assertTrue(component != null);

    // Check bind to class errors
    {
      final ErrorInfo errorInfo = (ErrorInfo)component.getClientProperty(CLIENT_PROP_CLASS_TO_BIND_ERROR);
      if(errorInfo != null){
        return errorInfo;
      }
    }

    // Check binding errors
    {
      final ErrorInfo error = (ErrorInfo)component.getClientProperty(CLIENT_PROP_BINDING_ERROR);
      if(error != null){
        return error;
      }
    }

    // General error
    {
      final ErrorInfo errorInfo = (ErrorInfo)component.getClientProperty(CLIENT_PROP_GENERAL_ERROR);
      if(errorInfo != null){
        return errorInfo;
      }
    }

    return null;
  }
}
