/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.wizard;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.PsiPropertiesProvider;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AlienFormFileException;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class Generator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.wizard.Generator");

  private Generator() {
  }

  /**
   * @param rootContainer output parameter; should be LwRootContainer[1]
   */
  public static FormProperty[] exposeForm(final Project project, final VirtualFile formFile, final LwRootContainer[] rootContainer) throws MyException{
    final Module module = ModuleUtil.findModuleForFile(formFile, project);
    LOG.assertTrue(module != null);

    final PsiPropertiesProvider propertiesProvider = new PsiPropertiesProvider(module);

    final Document doc = FileDocumentManager.getInstance().getDocument(formFile);
    final LwRootContainer _rootContainer;
    try {
      _rootContainer = Utils.getRootContainer(doc.getText(), propertiesProvider);
    }
    catch (AlienFormFileException e) {
      throw new MyException(e.getMessage());
    }
    catch (Exception e) {
      throw new MyException(UIDesignerBundle.message("error.cannot.process.form.file", e));
    }

    rootContainer[0] = _rootContainer;

    final String classToBind = _rootContainer.getClassToBind();
    if (classToBind == null) {
      throw new MyException(UIDesignerBundle.message("error.form.is.not.bound.to.a.class"));
    }

    final PsiClass boundClass = FormEditingUtil.findClassToBind(module, classToBind);
    if(boundClass == null){
      throw new MyException(UIDesignerBundle.message("error.bound.class.does.not.exist", classToBind));
    }

    final ArrayList<FormProperty> result = new ArrayList<>();
    final MyException[] exception = new MyException[1];

    FormEditingUtil.iterate(
      _rootContainer,
      new FormEditingUtil.ComponentVisitor<LwComponent>() {
        public boolean visit(final LwComponent component) {
          final String binding = component.getBinding();
          if (binding == null) {
            return true;
          }

          final PsiField[] fields = boundClass.getFields();
          PsiField field = null;
          for(int i = fields.length - 1; i >=0 ; i--){
            if(binding.equals(fields[i].getName())){
              field = fields[i];
              break;
            }
          }
          if(field == null){
            exception[0] = new MyException(UIDesignerBundle.message("error.field.not.found.in.class", binding, classToBind));
            return false;
          }

          final PsiClass fieldClass = getClassByType(field.getType());
          if (fieldClass == null) {
            exception[0] = new MyException(UIDesignerBundle.message("error.invalid.binding.field.type", binding, classToBind));
            return false;
          }

          if (instanceOf(fieldClass, JTextComponent.class.getName())) {
            result.add(new FormProperty(component, "getText", "setText", String.class.getName()));
          }
          else if (instanceOf(fieldClass, JCheckBox.class.getName())) {
            result.add(new FormProperty(component, "isSelected", "setSelected", boolean.class.getName()));
          }

          return true;
        }
      }
    );

    if (exception[0] != null) {
      throw exception[0];
    }

    return result.toArray(new FormProperty[result.size()]);
  }

  private static PsiClass getClassByType(final PsiType type) {
    if (!(type instanceof PsiClassType)) {
      return null;
    }
    return ((PsiClassType)type).resolve();
  }

  private static boolean instanceOf(final PsiClass jComponentClass, final String baseClassName) {
    for (PsiClass c = jComponentClass; c != null; c = c.getSuperClass()){
      if (baseClassName.equals(c.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Should be invoked in command and write action
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void generateDataBindingMethods(final WizardData data) throws MyException {
    if (data.myBindToNewBean) {
      data.myBeanClass = createBeanClass(data);
    }
    else {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(data.myBeanClass.getProject(), data.myBeanClass)) {
        return;
      }
    }

    final HashMap<String, String> binding2beanGetter = new HashMap<>();
    final HashMap<String, String> binding2beanSetter = new HashMap<>();

    final FormProperty2BeanProperty[] bindings = data.myBindings;
    for (final FormProperty2BeanProperty form2bean : bindings) {
      if (form2bean == null || form2bean.myBeanProperty == null) {
        continue;
      }

      // check that bean contains the property, and if not, try to add the property to the bean
      {
        final String setterName = PropertyUtil.suggestSetterName(form2bean.myBeanProperty.myName);
        final PsiMethod[] methodsByName = data.myBeanClass.findMethodsByName(setterName, true);
        if (methodsByName.length < 1) {
          // bean does not contain this property
          // try to add...

          LOG.assertTrue(!data.myBindToNewBean); // just generated bean class should contain all necessary properties

          if (!data.myBeanClass.isWritable()) {
            throw new MyException("Cannot add property to non writable class " + data.myBeanClass.getQualifiedName());
          }

          final StringBuffer membersBuffer = new StringBuffer();
          final StringBuffer methodsBuffer = new StringBuffer();

          final Project project = data.myBeanClass.getProject();
          final CodeStyleManager formatter = CodeStyleManager.getInstance(project);
          final JavaCodeStyleManager styler = JavaCodeStyleManager.getInstance(project);

          generateProperty(styler, form2bean.myBeanProperty.myName, form2bean.myBeanProperty.myType, membersBuffer,
                           methodsBuffer);

          final PsiClass fakeClass;
          try {
            fakeClass = JavaPsiFacade.getInstance(data.myBeanClass.getProject()).getElementFactory()
              .createClassFromText(membersBuffer.toString() + methodsBuffer.toString(), null);

            final PsiField[] fields = fakeClass.getFields();
            {
              final PsiElement result = data.myBeanClass.add(fields[0]);
              styler.shortenClassReferences(result);
              formatter.reformat(result);
            }

            final PsiMethod[] methods = fakeClass.getMethods();
            {
              final PsiElement result = data.myBeanClass.add(methods[0]);
              styler.shortenClassReferences(result);
              formatter.reformat(result);
            }
            {
              final PsiElement result = data.myBeanClass.add(methods[1]);
              styler.shortenClassReferences(result);
              formatter.reformat(result);
            }
          }
          catch (IncorrectOperationException e) {
            throw new MyException(e.getMessage());
          }
        }
      }

      final PsiMethod propertySetter = PropertyUtil.findPropertySetter(data.myBeanClass, form2bean.myBeanProperty.myName, false, true);
      final PsiMethod propertyGetter = PropertyUtil.findPropertyGetter(data.myBeanClass, form2bean.myBeanProperty.myName, false, true);

      if (propertyGetter == null) {
        // todo
        continue;
      }
      if (propertySetter == null) {
        // todo
        continue;
      }

      final String binding = form2bean.myFormProperty.getLwComponent().getBinding();
      binding2beanGetter.put(binding, propertyGetter.getName());
      binding2beanSetter.put(binding, propertySetter.getName());
    }

    final String dataBeanClassName = data.myBeanClass.getQualifiedName();

    final LwRootContainer[] rootContainer = new LwRootContainer[1];
    final FormProperty[] formProperties = exposeForm(data.myProject, data.myFormFile, rootContainer);

    final StringBuffer getDataBody = new StringBuffer();
    final StringBuffer setDataBody = new StringBuffer();
    final StringBuffer isModifiedBody = new StringBuffer();

    // iterate exposed formproperties

    for (final FormProperty formProperty : formProperties) {
      final String binding = formProperty.getLwComponent().getBinding();
      if (!binding2beanGetter.containsKey(binding)) {
        continue;
      }

      getDataBody.append("data.");
      getDataBody.append(binding2beanSetter.get(binding));
      getDataBody.append("(");
      getDataBody.append(binding);
      getDataBody.append(".");
      getDataBody.append(formProperty.getComponentPropertyGetterName());
      getDataBody.append("());\n");

      setDataBody.append(binding);
      setDataBody.append(".");
      setDataBody.append(formProperty.getComponentPropertySetterName());
      setDataBody.append("(data.");
      setDataBody.append(binding2beanGetter.get(binding));
      setDataBody.append("());\n");

      final String propertyClassName = formProperty.getComponentPropertyClassName();
      if ("boolean".equals(propertyClassName)) {
        isModifiedBody.append("if (");
        //
        isModifiedBody.append(binding);
        isModifiedBody.append(".");
        isModifiedBody.append(formProperty.getComponentPropertyGetterName());
        isModifiedBody.append("()");
        //
        isModifiedBody.append("!= ");
        //
        isModifiedBody.append("data.");
        isModifiedBody.append(binding2beanGetter.get(binding));
        isModifiedBody.append("()");
        //
        isModifiedBody.append(") return true;\n");
      }
      else {
        isModifiedBody.append("if (");
        //
        isModifiedBody.append(binding);
        isModifiedBody.append(".");
        isModifiedBody.append(formProperty.getComponentPropertyGetterName());
        isModifiedBody.append("()");
        //
        isModifiedBody.append("!= null ? ");
        //
        isModifiedBody.append("!");
        //
        isModifiedBody.append(binding);
        isModifiedBody.append(".");
        isModifiedBody.append(formProperty.getComponentPropertyGetterName());
        isModifiedBody.append("()");
        //
        isModifiedBody.append(".equals(");
        //
        isModifiedBody.append("data.");
        isModifiedBody.append(binding2beanGetter.get(binding));
        isModifiedBody.append("()");
        isModifiedBody.append(") : ");
        //
        isModifiedBody.append("data.");
        isModifiedBody.append(binding2beanGetter.get(binding));
        isModifiedBody.append("()");
        isModifiedBody.append("!= null");
        //
        isModifiedBody.append(") return true;\n");
      }
    }
    isModifiedBody.append("return false;\n");

    final String textOfMethods =
      "public void setData(" + dataBeanClassName + " data){\n" +
      setDataBody.toString() +
      "}\n" +
      "\n" +
      "public void getData(" + dataBeanClassName + " data){\n" +
      getDataBody.toString() +
      "}\n" +
      "\n" +
      "public boolean isModified(" + dataBeanClassName + " data){\n" +
      isModifiedBody.toString() +
      "}\n";

    // put them to the bound class

    final Module module = ModuleUtil.findModuleForFile(data.myFormFile, data.myProject);
    LOG.assertTrue(module != null);
    final PsiClass boundClass = FormEditingUtil.findClassToBind(module, rootContainer[0].getClassToBind());
    LOG.assertTrue(boundClass != null);

    if (!CommonRefactoringUtil.checkReadOnlyStatus(module.getProject(), boundClass)) {
      return;
    }

    // todo: check that this method does not exist yet

    final PsiClass fakeClass;
    try {
      fakeClass = JavaPsiFacade.getInstance(data.myProject).getElementFactory().createClassFromText(textOfMethods, null);

      final PsiMethod methodSetData = fakeClass.getMethods()[0];
      final PsiMethod methodGetData = fakeClass.getMethods()[1];
      final PsiMethod methodIsModified = fakeClass.getMethods()[2];

      final PsiMethod existing1 = boundClass.findMethodBySignature(methodSetData, false);
      final PsiMethod existing2 = boundClass.findMethodBySignature(methodGetData, false);
      final PsiMethod existing3 = boundClass.findMethodBySignature(methodIsModified, false);

      // warning already shown
      if (existing1 != null) {
        existing1.delete();
      }
      if (existing2 != null) {
        existing2.delete();
      }
      if (existing3 != null) {
        existing3.delete();
      }

      final CodeStyleManager formatter = CodeStyleManager.getInstance(module.getProject());
      final JavaCodeStyleManager styler = JavaCodeStyleManager.getInstance(module.getProject());

      final PsiElement setData = boundClass.add(methodSetData);
      styler.shortenClassReferences(setData);
      formatter.reformat(setData);

      final PsiElement getData = boundClass.add(methodGetData);
      styler.shortenClassReferences(getData);
      formatter.reformat(getData);

      if (data.myGenerateIsModified) {
        final PsiElement isModified = boundClass.add(methodIsModified);
        styler.shortenClassReferences(isModified);
        formatter.reformat(isModified);
      }

      final OpenFileDescriptor descriptor = new OpenFileDescriptor(setData.getProject(), setData.getContainingFile().getVirtualFile(), setData.getTextOffset());
      FileEditorManager.getInstance(data.myProject).openTextEditor(descriptor, true);
    }
    catch (IncorrectOperationException e) {
      throw new MyException(e.getMessage());
    }
  }

  @NotNull
  private static PsiClass createBeanClass(final WizardData wizardData) throws MyException {
    final PsiManager psiManager = PsiManager.getInstance(wizardData.myProject);

    final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(wizardData.myProject);
    final ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
    final VirtualFile sourceRoot = fileIndex.getSourceRootForFile(wizardData.myFormFile);
    if (sourceRoot == null) {
      throw new MyException(UIDesignerBundle.message("error.form.file.is.not.in.source.root"));
    }

    final PsiDirectory rootDirectory = psiManager.findDirectory(sourceRoot);
    LOG.assertTrue(rootDirectory != null);

    final PsiPackage aPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage(wizardData.myPackageName);
    if (aPackage == null) {
      throw new MyException(UIDesignerBundle.message("error.package.does.not.exist", wizardData.myPackageName));
    }

    PsiDirectory targetDir = null;

    final PsiDirectory[] directories = aPackage.getDirectories();
    for (final PsiDirectory psiDirectory : directories) {
      if (PsiTreeUtil.isAncestor(rootDirectory, psiDirectory, false)) {
        targetDir = psiDirectory;
        break;
      }
    }

    if (targetDir == null) {
      // todo
      throw new MyException(UIDesignerBundle.message("error.cannot.find.package", wizardData.myPackageName));
    }

    //noinspection HardCodedStringLiteral
    final String body =
      "public class " + wizardData.myShortClassName + "{\n" +
      "public " + wizardData.myShortClassName + "(){}\n" +
      "}";

    try {
      PsiFile sourceFile =
        PsiFileFactory.getInstance(psiManager.getProject()).createFileFromText(wizardData.myShortClassName + ".java", body);
      sourceFile = (PsiFile)targetDir.add(sourceFile);

      final PsiClass beanClass = ((PsiJavaFile)sourceFile).getClasses()[0];

      final ArrayList<String> properties = new ArrayList<>();
      final HashMap<String, String> property2fqClassName = new HashMap<>();

      final FormProperty2BeanProperty[] bindings = wizardData.myBindings;
      for (final FormProperty2BeanProperty binding : bindings) {
        if (binding == null || binding.myBeanProperty == null) {
          continue;
        }

        properties.add(binding.myBeanProperty.myName);

        // todo: handle "casts" ?

        final String propertyClassName = binding.myFormProperty.getComponentPropertyClassName();

        property2fqClassName.put(binding.myBeanProperty.myName, propertyClassName);
      }

      generateBean(beanClass, ArrayUtil.toStringArray(properties), property2fqClassName);

      return beanClass;
    }
    catch (IncorrectOperationException e) {
      throw new MyException(e.getMessage());
    }
  }

  // todo: inline
  private static void generateBean(
    final PsiClass aClass,
    final String[] properties,
    final HashMap<String, String> property2fqClassName
  ) throws MyException {
    final StringBuffer membersBuffer = new StringBuffer();
    final StringBuffer methodsBuffer = new StringBuffer();

    final CodeStyleManager formatter = CodeStyleManager.getInstance(aClass.getProject());
    final JavaCodeStyleManager styler = JavaCodeStyleManager.getInstance(aClass.getProject());

    for (final String property : properties) {
      LOG.assertTrue(property != null);
      final String type = property2fqClassName.get(property);
      LOG.assertTrue(type != null);

      generateProperty(styler, property, type, membersBuffer, methodsBuffer);
    }

    final PsiClass fakeClass;
    try {
      fakeClass = JavaPsiFacade.getInstance(aClass.getProject()).getElementFactory().createClassFromText(
        membersBuffer.toString() + methodsBuffer.toString(),
        null
      );

      final PsiField[] fields = fakeClass.getFields();
      for (final PsiField field : fields) {
        aClass.add(field);
      }

      final PsiMethod[] methods = fakeClass.getMethods();
      for (final PsiMethod method : methods) {
        aClass.add(method);
      }

      styler.shortenClassReferences(aClass);
      formatter.reformat(aClass);
    }
    catch (IncorrectOperationException e) {
      throw new MyException(e.getMessage());
    }
  }

  private static void generateProperty(final JavaCodeStyleManager codeStyleManager,
                                       final String property,
                                       final String type,
                                       @NonNls final StringBuffer membersBuffer, @NonNls final StringBuffer methodsBuffer) {
    final String field = codeStyleManager.suggestVariableName(VariableKind.FIELD, property, null, null).names[0];

    membersBuffer.append("private ");
    membersBuffer.append(type);
    membersBuffer.append(" ");
    membersBuffer.append(field);
    membersBuffer.append(";\n");

    // getter
    methodsBuffer.append("public ");
    methodsBuffer.append(type);
    methodsBuffer.append(" ");
    methodsBuffer.append(suggestGetterName(property, type));
    methodsBuffer.append("(){\n");
    methodsBuffer.append("return ");
    methodsBuffer.append(field);
    methodsBuffer.append(";}\n");

    // setter
    final String parameterName = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, property, null, null).names[0];
    methodsBuffer.append("public void ");
    methodsBuffer.append(PropertyUtil.suggestSetterName(property));
    methodsBuffer.append("(final ");
    methodsBuffer.append(type);
    methodsBuffer.append(" ");
    methodsBuffer.append(parameterName);
    methodsBuffer.append("){\n");
    if (parameterName.equals(field)) {
      methodsBuffer.append("this.");
    }
    methodsBuffer.append(field);
    methodsBuffer.append("=");
    methodsBuffer.append(parameterName);
    methodsBuffer.append(";}\n");
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String suggestGetterName(final String propertyName, final String propertyType) {
    return PropertyUtil.suggestGetterName(propertyName, "boolean".equals(propertyType) ? PsiType.BOOLEAN : null);
  }

  public static void prepareWizardData(final WizardData data, PsiClass boundClass) throws MyException {

    final PsiMethod[] allGetDataMethods = boundClass.findMethodsByName("getData", false);
    final PsiMethod[] allSetDataMethods = boundClass.findMethodsByName("setData", false);

    PsiMethod setDataMethod = null;
    PsiClass beanClass = null;

    // find get/set pair and bean class
    outer: for (int i = 0; i < allGetDataMethods.length; i++) {
      final PsiMethod _getMethod = allGetDataMethods[i];

      if (!PsiType.VOID.equals(_getMethod.getReturnType())) {
        continue;
      }

      final PsiParameter[] _getMethodParameters = _getMethod.getParameterList().getParameters();
      if (_getMethodParameters.length != 1) {
        continue;
      }

      final PsiClass _getParameterClass = getClassByType(_getMethodParameters[0].getType());
      if (_getParameterClass == null) {
        continue;
      }

      for (final PsiMethod _setMethod : allSetDataMethods) {
        if (!PsiType.VOID.equals(_setMethod.getReturnType())) {
          continue;
        }

        final PsiParameter[] _setMethodParameters = _setMethod.getParameterList().getParameters();
        if (_setMethodParameters.length != 1) {
          continue;
        }

        final PsiClass _setParameterClass = getClassByType(_setMethodParameters[0].getType());
        if (_setParameterClass != _getParameterClass) {
          continue;
        }

        // pair found !!!

        setDataMethod = _setMethod;
        beanClass = _getParameterClass;
        break outer;
      }
    }

    if (beanClass == null) {
      // nothing found
      return;
    }

    data.myBindToNewBean = false;
    data.myBeanClass = beanClass;

    // parse setData() and try to associate fields with bean
    {
      final PsiCodeBlock body = setDataMethod.getBody();
      if (body == null) {
        return;
      }

      final PsiElement[] children = body.getChildren();
      for (PsiElement child : children) {
        // Parses sequences like: a.foo(b.bar());
        final PsiField bindingField;

        if (!(child instanceof PsiExpressionStatement)) {
          continue;
        }

        final PsiExpression expression = ((PsiExpressionStatement)child).getExpression();
        if (!(expression instanceof PsiMethodCallExpression)) {
          continue;
        }

        final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)expression;

        // find binding field ('a')
        int index = -1;
        {
          final PsiElement psiElement = getObjectForWhichMethodWasCalled(callExpression);
          if (!(psiElement instanceof PsiField)) {
            continue;
          }

          if (((PsiField)psiElement).getContainingClass() != boundClass) {
            continue;
          }

          bindingField = (PsiField)psiElement;

          // find binding for this field
          final FormProperty2BeanProperty[] bindings = data.myBindings;
          for (int j = 0; j < bindings.length; j++) {
            final FormProperty2BeanProperty binding = bindings[j];
            if (bindingField.getName().equals(binding.myFormProperty.getLwComponent().getBinding())) {
              index = j;
              break;
            }
          }
        }

        if (index == -1) {
          continue;
        }

        // find 'bar()'
        {
          final PsiReferenceParameterList parameterList = callExpression.getMethodExpression().getParameterList();
          if (parameterList == null) {
            continue;
          }

          final PsiExpressionList argumentList = callExpression.getArgumentList();
          if (argumentList == null) {
            continue;
          }

          final PsiExpression[] expressions = argumentList.getExpressions();
          if (expressions == null || expressions.length != 1) {
            continue;
          }

          if (!(expressions[0]instanceof PsiMethodCallExpression)) {
            continue;
          }

          final PsiMethodCallExpression callExpression2 = ((PsiMethodCallExpression)expressions[0]);

          // check that 'b' is parameter
          final PsiElement psiElement = getObjectForWhichMethodWasCalled(callExpression2);
          if (!(psiElement instanceof PsiParameter)) {
            continue;
          }

          final PsiMethod barMethod = ((PsiMethod)callExpression2.getMethodExpression().resolve());
          if (barMethod == null) {
            continue;
          }

          if (!PropertyUtil.isSimplePropertyGetter(barMethod)) {
            continue;
          }

          final String propertyName = PropertyUtil.getPropertyName(barMethod);

          // There are two possible types: boolean and java.lang.String
          String typeName = barMethod.getReturnType().getCanonicalText();
          if(!"boolean".equals(typeName) && !"java.lang.String".equals(typeName)){
            continue;
          }

          data.myBindings[index].myBeanProperty = new BeanProperty(propertyName, typeName);
        }
      }
    }
  }

  private static PsiElement getObjectForWhichMethodWasCalled(final PsiMethodCallExpression callExpression) {
    final PsiExpression qualifierExpression = callExpression.getMethodExpression().getQualifierExpression();
    if (!(qualifierExpression instanceof PsiReferenceExpression)) {
      return null;
    }
    return ((PsiReferenceExpression)qualifierExpression).resolve();
  }

  public static final class MyException extends Exception{
    public MyException(final String message) {
      super(message);
    }
  }
}
