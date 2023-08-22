// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.inspections.FormInspectionTool;
import com.intellij.uiDesigner.lw.IButtonGroup;
import com.intellij.uiDesigner.lw.IComponent;
import com.intellij.uiDesigner.lw.IContainer;
import com.intellij.uiDesigner.lw.IRootContainer;
import com.intellij.uiDesigner.quickFixes.*;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public final class ErrorAnalyzer {
  private static final Logger LOG = Logger.getInstance(ErrorAnalyzer.class);

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

  @NonNls public static final String CLIENT_PROP_ERROR_ARRAY = "errorArray";

  private ErrorAnalyzer() {
  }

  static void analyzeErrors(@NotNull GuiEditor editor, final IRootContainer rootContainer, @Nullable final ProgressIndicator progress) {
    analyzeErrors(editor.getModule(), editor.getFile(), editor, rootContainer, progress);
  }

  /**
   * @param editor if null, no quick fixes are created. This is used in form to source compiler.
   */
  public static void analyzeErrors(@NotNull final Module module,
                                   @NotNull final VirtualFile formFile,
                                   @Nullable final GuiEditor editor,
                                   @NotNull final IRootContainer rootContainer,
                                   @Nullable final ProgressIndicator progress) {
    if (module.isDisposed()) {
      return;
    }

    // 1. Validate class to bind
    final String classToBind = rootContainer.getClassToBind();
    final PsiClass psiClass;
    if (classToBind != null) {
      psiClass = FormEditingUtil.findClassToBind(module, classToBind);
      if (psiClass == null) {
        final QuickFix[] fixes = editor != null ? new QuickFix[]{new CreateClassToBindFix(editor, classToBind)} : QuickFix.EMPTY_ARRAY;
        final ErrorInfo errorInfo = new ErrorInfo(null, null, UIDesignerBundle.message("error.class.does.not.exist", classToBind),
                                                  HighlightDisplayLevel.ERROR, fixes);
        rootContainer.putClientProperty(CLIENT_PROP_CLASS_TO_BIND_ERROR, errorInfo);
      }
      else {
        rootContainer.putClientProperty(CLIENT_PROP_CLASS_TO_BIND_ERROR, null);
      }
    }
    else {
      rootContainer.putClientProperty(CLIENT_PROP_CLASS_TO_BIND_ERROR, null);
      psiClass = null;
    }

    // 2. Validate bindings to fields
    // field name -> error message
    final ArrayList<String> usedBindings = new ArrayList<>(); // for performance reasons
    final Set<IButtonGroup> processedGroups = new HashSet<>();
    FormEditingUtil.iterate(
      rootContainer,
      new FormEditingUtil.ComponentVisitor<>() {
        @Override
        public boolean visit(final IComponent component) {
          if (progress != null && progress.isCanceled()) return false;

          // Reset previous error (if any)
          component.putClientProperty(CLIENT_PROP_BINDING_ERROR, null);

          final String binding = component.getBinding();

          // a. Check that field exists and field is not static
          if (psiClass != null && binding != null) {
            if (validateFieldInClass(component, binding, component.getComponentClassName(), psiClass, editor, module)) return true;
          }

          // b. Check that binding is unique
          if (binding != null) {
            if (usedBindings.contains(binding)) {
              // TODO[vova] implement
              component.putClientProperty(
                CLIENT_PROP_BINDING_ERROR,
                new ErrorInfo(
                  component, null, UIDesignerBundle.message("error.binding.already.exists", binding),
                  HighlightDisplayLevel.ERROR,
                  QuickFix.EMPTY_ARRAY
                )
              );
              return true;
            }

            usedBindings.add(binding);
          }

          IButtonGroup group = FormEditingUtil.findGroupForComponent(rootContainer, component);
          if (group != null && !processedGroups.contains(group)) {
            processedGroups.add(group);
            if (group.isBound()) {
              validateFieldInClass(component, group.getName(), ButtonGroup.class.getName(), psiClass, editor, module);
            }
          }

          return true;
        }
      }
    );
    if (progress != null) progress.checkCanceled();

    // Check that there are no panels in XY with children
    FormEditingUtil.iterate(
      rootContainer,
      new FormEditingUtil.ComponentVisitor<>() {
        @Override
        public boolean visit(final IComponent component) {
          if (progress != null && progress.isCanceled()) return false;

          // Clear previous error (if any)
          component.putClientProperty(CLIENT_PROP_ERROR_ARRAY, null);

          if (!(component instanceof IContainer container)) {
            return true;
          }

          if (container instanceof IRootContainer rootContainer) {
            if (rootContainer.getComponentCount() > 1) {
              // TODO[vova] implement
              putError(component, new ErrorInfo(
                component, null, UIDesignerBundle.message("error.multiple.toplevel.components"),
                HighlightDisplayLevel.ERROR,
                QuickFix.EMPTY_ARRAY
              ));
            }
          }
          else if (container.isXY() && container.getComponentCount() > 0) {
            // TODO[vova] implement
            putError(component, new ErrorInfo(
                       component, null, UIDesignerBundle.message("error.panel.not.laid.out"),
                       HighlightDisplayLevel.ERROR,
                       QuickFix.EMPTY_ARRAY
                     )
            );
          }
          return true;
        }
      }
    );
    if (progress != null) progress.checkCanceled();

    try {
      // Run inspections for form elements
      final PsiFile formPsiFile = PsiManager.getInstance(module.getProject()).findFile(formFile);
      if (formPsiFile != null && rootContainer instanceof RadRootContainer) {
        final List<FormInspectionTool> formInspectionTools = new ArrayList<>();
        for (FormInspectionTool formInspectionTool : FormInspectionTool.EP_NAME.getExtensionList()) {
          if (formInspectionTool.isActive(formPsiFile) && !isSuppressed(rootContainer, formInspectionTool, null)) {
            formInspectionTools.add(formInspectionTool);
          }
        }

        if (!formInspectionTools.isEmpty() && editor != null) {
          for (FormInspectionTool tool : formInspectionTools) {
            tool.startCheckForm(rootContainer);
          }
          FormEditingUtil.iterate(
            rootContainer,
            (FormEditingUtil.ComponentVisitor<RadComponent>)(RadComponent component) -> {
              if (progress != null && progress.isCanceled()) return false;

              for (FormInspectionTool tool : formInspectionTools) {
                if (isSuppressed(rootContainer, tool, component.getId())) continue;
                ErrorInfo[] errorInfos = tool.checkComponent(editor, component);
                if (errorInfos != null) {
                  ArrayList<ErrorInfo> errorList = getErrorInfos(component);
                  if (errorList == null) {
                    errorList = new ArrayList<>();
                    component.putClientProperty(CLIENT_PROP_ERROR_ARRAY, errorList);
                  }
                  Collections.addAll(errorList, errorInfos);
                }
              }
              return true;
            }
          );
          for (FormInspectionTool tool : formInspectionTools) {
            tool.doneCheckForm(rootContainer);
          }
        }
      }
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public static boolean isSuppressed(@NotNull IRootContainer rootContainer,
                                     @NotNull FormInspectionTool formInspectionTool, String componentId) {
    String shortName = formInspectionTool.getShortName();
    if (rootContainer.isInspectionSuppressed(shortName, componentId)) return true;
    if (formInspectionTool instanceof LocalInspectionTool) {
      String alternativeID = ((LocalInspectionTool)formInspectionTool).getAlternativeID();
      if (!Objects.equals(alternativeID, shortName)) {
        return rootContainer.isInspectionSuppressed(alternativeID, componentId);
      }
    }
    return false;
  }

  private static boolean validateFieldInClass(final IComponent component, final String fieldName, final String fieldClassName,
                                              final PsiClass psiClass, final GuiEditor editor, final Module module) {
    final PsiField[] fields = psiClass.getFields();
    PsiField field = null;
    for(int i = fields.length - 1; i >=0 ; i--){
      if(fieldName.equals(fields[i].getName())){
        field = fields[i];
        break;
      }
    }
    if(field == null){
      final QuickFix[] fixes = editor != null
                               ? new QuickFix[]{ new CreateFieldFix(editor, psiClass, fieldClassName, fieldName) }
                               : QuickFix.EMPTY_ARRAY;
      component.putClientProperty(
       CLIENT_PROP_BINDING_ERROR,
       new ErrorInfo(
         component, null, UIDesignerBundle.message("error.no.field.in.class", fieldName, psiClass.getQualifiedName()),
         HighlightDisplayLevel.ERROR,
         fixes
       )
      );
      return true;
    }
    else if(field.hasModifierProperty(PsiModifier.STATIC)){
      component.putClientProperty(
        CLIENT_PROP_BINDING_ERROR,
        new ErrorInfo(
          component, null, UIDesignerBundle.message("error.cant.bind.to.static", fieldName),
          HighlightDisplayLevel.ERROR,
          QuickFix.EMPTY_ARRAY
        )
      );
      return true;
    }

    // Check that field has correct fieldType
    try {
      final String className = fieldClassName.replace('$', '.'); // workaround for PSI
      final PsiType componentType = JavaPsiFacade.getInstance(module.getProject()).getElementFactory().createTypeFromText(
        className,
        null
      );
      final PsiType fieldType = field.getType();
      if(!fieldType.isAssignableFrom(componentType)){
        final QuickFix[] fixes = editor != null ? new QuickFix[]{
          new ChangeFieldTypeFix(editor, field, componentType)
        } : QuickFix.EMPTY_ARRAY;
        component.putClientProperty(
          CLIENT_PROP_BINDING_ERROR,
          new ErrorInfo(
            component, null, UIDesignerBundle.message("error.bind.incompatible.types", fieldType.getPresentableText(), className),
            HighlightDisplayLevel.ERROR,
            fixes
          )
        );
        return true;
      }
    }
    catch (IncorrectOperationException ignored) {
    }

    if (component.isCustomCreate() && FormEditingUtil.findCreateComponentsMethod(psiClass) == null) {
      final QuickFix[] fixes = editor != null ? new QuickFix[]{
        new GenerateCreateComponentsFix(editor, psiClass)
      } : QuickFix.EMPTY_ARRAY;
      component.putClientProperty(
        CLIENT_PROP_BINDING_ERROR,
        new ErrorInfo(
          component, "Custom Create",
          UIDesignerBundle.message("error.no.custom.create.method"), HighlightDisplayLevel.ERROR,
          fixes));
      return true;
    }
    return false;
  }

  private static void putError(final IComponent component, final ErrorInfo errorInfo) {
    ArrayList<ErrorInfo> errorList = getErrorInfos(component);
    if (errorList == null) {
      errorList = new ArrayList<>();
      component.putClientProperty(CLIENT_PROP_ERROR_ARRAY, errorList);
    }

    errorList.add(errorInfo);
  }

  /**
   * @return first ErrorInfo for the specified component. If component doesn't contain
   * any error then the method returns {@code null}.
   */
  @Nullable
  public static ErrorInfo getErrorForComponent(@NotNull final IComponent component){
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
      final ArrayList<ErrorInfo> errorInfo = getErrorInfos(component);
      if(errorInfo != null && errorInfo.size() > 0){
        return errorInfo.get(0);
      }
    }

    return null;
  }

  public static ErrorInfo @NotNull [] getAllErrorsForComponent(@NotNull IComponent component) {
    List<ErrorInfo> result = new ArrayList<>();
    ErrorInfo errorInfo = (ErrorInfo)component.getClientProperty(CLIENT_PROP_CLASS_TO_BIND_ERROR);
    if (errorInfo != null) {
      result.add(errorInfo);
    }
    errorInfo = (ErrorInfo)component.getClientProperty(CLIENT_PROP_BINDING_ERROR);
    if (errorInfo != null) {
      result.add(errorInfo);
    }
    final ArrayList<ErrorInfo> errorInfos = getErrorInfos(component);
    if (errorInfos != null) {
      result.addAll(errorInfos);
    }
    return result.toArray(ErrorInfo.EMPTY_ARRAY);
  }

  private static ArrayList<ErrorInfo> getErrorInfos(final IComponent component) {
    //noinspection unchecked
    return (ArrayList<ErrorInfo>)component.getClientProperty(CLIENT_PROP_ERROR_ARRAY);
  }

  @Nullable
  public static HighlightDisplayLevel getHighlightDisplayLevel(final Project project, @NotNull final RadComponent component) {
    HighlightDisplayLevel displayLevel = null;
    for(ErrorInfo errInfo: getAllErrorsForComponent(component)) {
      if (displayLevel == null || SeverityRegistrar.getSeverityRegistrar(project).compare(errInfo.getHighlightDisplayLevel().getSeverity(), displayLevel.getSeverity()) > 0) {
        displayLevel = errInfo.getHighlightDisplayLevel();
      }
    }
    return displayLevel;
  }
}
