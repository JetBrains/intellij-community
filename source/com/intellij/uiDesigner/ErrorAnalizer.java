package com.intellij.uiDesigner;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IContainer;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.quickFixes.CreateClassToBindFix;
import com.intellij.uiDesigner.quickFixes.CreateFieldFix;
import com.intellij.uiDesigner.quickFixes.QuickFix;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ErrorAnalizer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.ErrorAnalyzer");

  /**
   * Value {@link ErrorInfo}
   */
  public static final String CLIENT_PROP_CLASS_TO_BIND_ERROR = "classToBindError";
  /**
   * Value {@link ErrorInfo}
   */
  public static final String CLIENT_PROP_BINDING_ERROR = "bindingError";
  /**
   * Value {@link ErrorInfo}
   */
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
        final ErrorInfo errorInfo = new ErrorInfo("Class \"" + classToBind + "\" does not exist", fixes);
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
                 "Field \"" + binding + "\" does not exist in class \"" + classToBind +"\"",
                 fixes
               )
              );
              return true;
            }
            else if(field.hasModifierProperty(PsiModifier.STATIC)){
              component.putClientProperty(
                CLIENT_PROP_BINDING_ERROR,
                new ErrorInfo(
                  "Cannot bind to static field \"" + binding + "\"",
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
                // TODO[vova] implement
                component.putClientProperty(
                  CLIENT_PROP_BINDING_ERROR,
                  new ErrorInfo(
                    "Incompatible types. Found \"" + fieldType.getPresentableText() + "\", required \"" + className + "\"",
                    QuickFix.EMPTY_ARRAY
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
                "Binding to field \"" + binding + "\" already exists",
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
          if(!(component instanceof IContainer)){
            return true;
          }

          // Clear previous error (if any)
          component.putClientProperty(CLIENT_PROP_GENERAL_ERROR, null);

          final IContainer container = (IContainer)component;
          if(container instanceof IRootContainer){
            final IRootContainer rootContainer = (IRootContainer)container;
            if(rootContainer.getComponentCount() > 1){
              // TODO[vova] implement
              component.putClientProperty(
                CLIENT_PROP_GENERAL_ERROR,
                new ErrorInfo(
                  "Form cannot be compiled because it contains more than one component at the top level",
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
                "Form cannot be compiled until this panel is layed out in a grid",
                QuickFix.EMPTY_ARRAY
              )
            );
          }
          return true;
        }
      }
    );
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
