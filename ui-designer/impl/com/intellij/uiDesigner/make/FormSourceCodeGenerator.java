package com.intellij.uiDesigner.make;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.compiler.*;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.shared.BorderType;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public final class FormSourceCodeGenerator {
  @NonNls private StringBuffer myBuffer;
  private Stack<Boolean> myIsFirstParameterStack;
  private final Project myProject;
  private final ArrayList<FormErrorInfo> myErrors;
  private boolean myNeedLoadLabelText;
  private boolean myNeedLoadButtonText;

  private static Map<Class, LayoutSourceGenerator> ourComponentLayoutCodeGenerators = new HashMap<Class, LayoutSourceGenerator>();
  private static Map<String, LayoutSourceGenerator> ourContainerLayoutCodeGenerators = new HashMap<String, LayoutSourceGenerator>();
  @NonNls private static TIntObjectHashMap<String> ourFontStyleMap = new TIntObjectHashMap<String>();
  @NonNls private static TIntObjectHashMap<String> ourTitleJustificationMap = new TIntObjectHashMap<String>();
  @NonNls private static TIntObjectHashMap<String> ourTitlePositionMap = new TIntObjectHashMap<String>();

  static {
    ourComponentLayoutCodeGenerators.put(LwSplitPane.class, new SplitPaneLayoutSourceGenerator());
    ourComponentLayoutCodeGenerators.put(LwTabbedPane.class, new TabbedPaneLayoutSourceGenerator());
    ourComponentLayoutCodeGenerators.put(LwScrollPane.class, new ScrollPaneLayoutSourceGenerator());
    ourComponentLayoutCodeGenerators.put(LwToolBar.class, new ToolBarLayoutSourceGenerator());

    ourFontStyleMap.put(Font.PLAIN, "java.awt.Font.PLAIN");
    ourFontStyleMap.put(Font.BOLD, "java.awt.Font.BOLD");
    ourFontStyleMap.put(Font.ITALIC, "java.awt.Font.ITALIC");
    ourFontStyleMap.put(Font.BOLD | Font.ITALIC, "java.awt.Font.BOLD | java.awt.Font.ITALIC");

    ourTitlePositionMap.put(0, "javax.swing.border.TitledBorder.DEFAULT_POSITION");
    ourTitlePositionMap.put(1, "javax.swing.border.TitledBorder.ABOVE_TOP");
    ourTitlePositionMap.put(2, "javax.swing.border.TitledBorder.TOP");
    ourTitlePositionMap.put(3, "javax.swing.border.TitledBorder.BELOW_TOP");
    ourTitlePositionMap.put(4, "javax.swing.border.TitledBorder.ABOVE_BOTTOM");
    ourTitlePositionMap.put(5, "javax.swing.border.TitledBorder.BOTTOM");
    ourTitlePositionMap.put(6, "javax.swing.border.TitledBorder.BELOW_BOTTOM");

    ourTitleJustificationMap.put(0, "javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION");
    ourTitleJustificationMap.put(1, "javax.swing.border.TitledBorder.LEFT");
    ourTitleJustificationMap.put(2, "javax.swing.border.TitledBorder.CENTER");
    ourTitleJustificationMap.put(3, "javax.swing.border.TitledBorder.RIGHT");
    ourTitleJustificationMap.put(4, "javax.swing.border.TitledBorder.LEADING");
    ourTitleJustificationMap.put(5, "javax.swing.border.TitledBorder.TRAILING");
  }

  public FormSourceCodeGenerator(@NotNull final Project project){
    myProject = project;
    myErrors = new ArrayList<FormErrorInfo>();
  }

  public void generate(final VirtualFile formFile) {
    myNeedLoadLabelText = false;
    myNeedLoadButtonText = false;

    final Module module = VfsUtil.getModuleForFile(myProject, formFile);
    if (module == null) {
      return;
    }

    // ensure that new instances of generators are used for every run
    ourContainerLayoutCodeGenerators.clear();
    ourContainerLayoutCodeGenerators.put(UIFormXmlConstants.LAYOUT_INTELLIJ, new GridLayoutSourceGenerator());
    ourContainerLayoutCodeGenerators.put(UIFormXmlConstants.LAYOUT_GRIDBAG, new GridBagLayoutSourceGenerator());
    ourContainerLayoutCodeGenerators.put(UIFormXmlConstants.LAYOUT_BORDER, new BorderLayoutSourceGenerator());
    ourContainerLayoutCodeGenerators.put(UIFormXmlConstants.LAYOUT_FLOW, new FlowLayoutSourceGenerator());
    ourContainerLayoutCodeGenerators.put(UIFormXmlConstants.LAYOUT_CARD, new CardLayoutSourceGenerator());
    ourContainerLayoutCodeGenerators.put(UIFormXmlConstants.LAYOUT_FORM, new FormLayoutSourceGenerator());
    myErrors.clear();

    final PsiPropertiesProvider propertiesProvider = new PsiPropertiesProvider(module);

    final Document doc = FileDocumentManager.getInstance().getDocument(formFile);
    final LwRootContainer rootContainer;
    try {
      rootContainer = Utils.getRootContainer(doc.getText(), propertiesProvider);
    }
    catch (AlienFormFileException ignored) {
      // ignoring this file
      return;
    }
    catch (Exception e) {
      myErrors.add(new FormErrorInfo(null, UIDesignerBundle.message("error.cannot.process.form.file", e)));
      return;
    }

    if (rootContainer.getClassToBind() == null) {
      // form skipped - no class to bind
      return;
    }

    ErrorAnalyzer.analyzeErrors(module, formFile, null, rootContainer, null);
    FormEditingUtil.iterate(
      rootContainer,
      new FormEditingUtil.ComponentVisitor<LwComponent>() {
        public boolean visit(final LwComponent iComponent) {
          final ErrorInfo errorInfo = ErrorAnalyzer.getErrorForComponent(iComponent);
          if (errorInfo != null) {
            String message;
            if (iComponent.getBinding() != null) {
              message = UIDesignerBundle.message("error.for.component", iComponent.getBinding(), errorInfo.myDescription);
            }
            else {
              message = errorInfo.myDescription;
            }
            myErrors.add(new FormErrorInfo(iComponent.getId(), message));
          }
          return true;
        }
      }
    );

    if (myErrors.size() != 0) {
      return;
    }

    try {
      _generate(rootContainer, module);
    }
    catch (ClassToBindNotFoundException e) {
      // ignored
      return;
    }
    catch (CodeGenerationException e) {
      myErrors.add(new FormErrorInfo(e.getComponentId(), e.getMessage()));
    }
    catch (IncorrectOperationException e) {
      myErrors.add(new FormErrorInfo(null, e.getMessage()));
    }
  }

  public ArrayList<FormErrorInfo> getErrors() {
    return myErrors;
  }

  private void _generate(final LwRootContainer rootContainer, final Module module) throws CodeGenerationException, IncorrectOperationException{
    myBuffer = new StringBuffer();
    myIsFirstParameterStack = new Stack<Boolean>();

    final HashMap<LwComponent,String> component2variable = new HashMap<LwComponent,String>();
    final TObjectIntHashMap<String> class2variableIndex = new TObjectIntHashMap<String>();
    final HashMap<String,LwComponent> id2component = new HashMap<String, LwComponent>();

    if (rootContainer.getComponentCount() != 1) {
      throw new CodeGenerationException(null, UIDesignerBundle.message("error.one.toplevel.component.required"));
    }
    final LwComponent topComponent = (LwComponent)rootContainer.getComponent(0);
    String id = Utils.findNotEmptyPanelWithXYLayout(topComponent);
    if (id != null) {
      throw new CodeGenerationException(id, UIDesignerBundle.message("error.nonempty.xy.panels.found"));
    }

    final PsiClass classToBind = FormEditingUtil.findClassToBind(module, rootContainer.getClassToBind());
    if (classToBind == null) {
      throw new ClassToBindNotFoundException(UIDesignerBundle.message("error.class.to.bind.not.found", rootContainer.getClassToBind()));
    }

    if (Utils.getCustomCreateComponentCount(rootContainer) > 0) {
      if (FormEditingUtil.findCreateComponentsMethod(classToBind) == null) {
        throw new CodeGenerationException(null, UIDesignerBundle.message("error.no.custom.create.method"));
      }
      myBuffer.append(AsmCodeGenerator.CREATE_COMPONENTS_METHOD_NAME).append("();");
    }

    generateSetupCodeForComponent(topComponent,
                                  component2variable,
                                  class2variableIndex,
                                  id2component, module, classToBind);
    generateComponentReferenceProperties(topComponent, component2variable, class2variableIndex, id2component, classToBind);
    generateButtonGroups(rootContainer, component2variable, class2variableIndex, id2component, classToBind);

    final String methodText = myBuffer.toString();

    final PsiManager psiManager = PsiManager.getInstance(module.getProject());
    final PsiElementFactory elementFactory = psiManager.getElementFactory();

    final PsiClass newClass = (PsiClass) classToBind.copy();

    cleanup(newClass);

    // [anton] the comments are written according to the SCR 26896  
    final PsiClass fakeClass = elementFactory.createClassFromText(
      "{\n" +
      "// GUI initializer generated by " + ApplicationNamesInfo.getInstance().getFullProductName() + " GUI Designer\n" +
      "// >>> IMPORTANT!! <<<\n" +
      "// DO NOT EDIT OR ADD ANY CODE HERE!\n" +
      "" + AsmCodeGenerator.SETUP_METHOD_NAME + "();\n" +
      "}\n" +
      "\n" +
      "/** Method generated by " + ApplicationNamesInfo.getInstance().getFullProductName() + " GUI Designer\n" +
      " * >>> IMPORTANT!! <<<\n" +
      " * DO NOT edit this method OR call it in your code!\n" +
      " * @noinspection HardCodedStringLiteral\n" +
      " */\n" +
      "private void " + AsmCodeGenerator.SETUP_METHOD_NAME + "()\n" +
      "{\n" +
      methodText +
      "}\n",
      null
    );

    final PsiMethod method = (PsiMethod) newClass.add(fakeClass.getMethods()[0]);
    PsiElement initializer = null;

    // don't generate initializer block if $$$setupUI$$$() is called explicitly from one of the constructors
    boolean needInitializer = true;
    for(PsiMethod constructor: newClass.getConstructors()) {
      if (containsMethodIdentifier(constructor, method)) {
        needInitializer = false;
        break;
      }
    }

    if (needInitializer) {
      initializer = newClass.addBefore(fakeClass.getInitializers()[0], method);
    }

    @NonNls final String grcMethodText = "public javax.swing.JComponent " + AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME +
                                         "() { return " + topComponent.getBinding() + "; }";
    generateMethodIfRequired(newClass, method, AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME, grcMethodText, topComponent.getBinding() != null);

    final String loadButtonTextMethodText = getLoadMethodText(AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD, AbstractButton.class, module);
    generateMethodIfRequired(newClass, method, AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD, loadButtonTextMethodText, myNeedLoadButtonText);
    final String loadLabelTextMethodText = getLoadMethodText(AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD, JLabel.class, module);
    generateMethodIfRequired(newClass, method, AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD, loadLabelTextMethodText, myNeedLoadLabelText);

    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(module.getProject());
    codeStyleManager.shortenClassReferences(method);
    if (initializer != null) {
      codeStyleManager.shortenClassReferences(initializer);
    }

    codeStyleManager.reformat(newClass);
    final String newText = newClass.getText();
    final String oldText = classToBind.getText();
    if (!newText.equals(oldText)) {
      classToBind.replace(newClass);
    }
  }

  private String getLoadMethodText(final String methodName, final Class componentClass, Module module) {
    final boolean needIndex = haveSetDisplayedMnemonic(componentClass, module);
    return "private void " + methodName + "(" + componentClass.getName() + " component, java.lang.String text) {" +
                                        "  StringBuffer result = new StringBuffer(); " +
                                        "  boolean haveMnemonic = false;    " +
                                        "  char mnemonic = '\\0';" +
                                        (needIndex ? "int mnemonicIndex = -1;" : "") +
                                        "  for(int i=0; i<text.length(); i++) {" +
                                        "    if (text.charAt(i) == '&') {" +
                                        "      i++;" +
                                        "      if (i == text.length()) break;" +
                                        "      if (!haveMnemonic && text.charAt(i) != '&') {" +
                                        "        haveMnemonic = true;" +
                                        "        mnemonic = text.charAt(i);" +
                                        (needIndex ? "mnemonicIndex = result.length();" : "") +
                                        "      }" +
                                        "    }" +
                                        "    result.append(text.charAt(i));" +
                                        "  }" +
                                        "  component.setText(result.toString()); " +
                                        "  if (haveMnemonic) {" +
                                        (componentClass.equals(AbstractButton.class)
                                          ? "        component.setMnemonic(mnemonic);"
                                          : "        component.setDisplayedMnemonic(mnemonic);") +
                                        (needIndex ? "component.setDisplayedMnemonicIndex(mnemonicIndex);" : "") +
                                        "} }";
  }

  private void generateMethodIfRequired(PsiClass aClass, PsiMethod anchor, final String methodName, String methodText, boolean condition) throws IncorrectOperationException {
    PsiElementFactory elementFactory = PsiManager.getInstance(myProject).getElementFactory();
    PsiMethod newMethod = null;
    PsiMethod[] oldMethods = aClass.findMethodsByName(methodName, false);
    if (!condition) {
      for(PsiMethod oldMethod: oldMethods) {
        oldMethod.delete();
      }
    }
    else {
      newMethod = elementFactory.createMethodFromText(methodText, aClass);
      if (oldMethods.length > 0) {
        oldMethods [0].replace(newMethod);
      }
      else {
        aClass.addAfter(newMethod, anchor);
      }
    }
    if (newMethod != null) {
      CodeStyleManager csm = CodeStyleManager.getInstance(myProject);
      csm.shortenClassReferences(newMethod);
    }
  }

  public static void cleanup(final PsiClass aClass) throws IncorrectOperationException{
    final PsiMethod[] methods = aClass.findMethodsByName(AsmCodeGenerator.SETUP_METHOD_NAME, false);
    for (final PsiMethod method: methods) {
      final PsiClassInitializer[] initializers = aClass.getInitializers();
      for (final PsiClassInitializer initializer : initializers) {
        if (containsMethodIdentifier(initializer, method)) {
          initializer.delete();
        }
      }

      method.delete();
    }

    deleteMethods(aClass, AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME);
    deleteMethods(aClass, AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD);
    deleteMethods(aClass, AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD);
  }

  private static void deleteMethods(final PsiClass aClass, final String methodName) throws IncorrectOperationException {
    final PsiMethod[] grcMethods = aClass.findMethodsByName(methodName, false);
    for(final PsiMethod grcMethod: grcMethods) {
      grcMethod.delete();
    }
  }

  private static boolean containsMethodIdentifier(final PsiElement element, final PsiMethod setupMethod) {
    if (element instanceof PsiMethodCallExpression) {
      final PsiMethod psiMethod = ((PsiMethodCallExpression)element).resolveMethod();
      if (setupMethod.equals(psiMethod)){
        return true;
      }
    }
    final PsiElement[] children = element.getChildren();
    for (int i = children.length - 1; i >= 0; i--) {
      if (containsMethodIdentifier(children[i], setupMethod)) {
        return true;
      }
    }
    return false;
  }


  private void generateSetupCodeForComponent(final LwComponent component,
                                             final HashMap<LwComponent, String> component2TempVariable,
                                             final TObjectIntHashMap<String> class2variableIndex,
                                             final HashMap<String, LwComponent> id2component,
                                             final Module module,
                                             final PsiClass aClass) throws CodeGenerationException{
    id2component.put(component.getId(), component);
    GlobalSearchScope globalSearchScope = module.getModuleWithDependenciesAndLibrariesScope(false);

    final LwContainer parent = component.getParent();

    final String variable = getVariable(component, component2TempVariable, class2variableIndex, aClass);
    final String componentClass = component instanceof LwNestedForm
                                  ? getNestedFormClass(module, (LwNestedForm) component)
                                  : getComponentLayoutGenerator(component.getParent()).mapComponentClass(component.getComponentClassName());

    if (component.isCustomCreate() && component.getBinding() == null) {
      throw new CodeGenerationException(component.getId(), UIDesignerBundle.message("error.custom.create.no.binding"));
    }

    if (!component.isCustomCreate()) {
      final String binding = component.getBinding();
      if (binding != null) {
        myBuffer.append(binding);
      }
      else {
        myBuffer.append("final ");
        myBuffer.append(componentClass);
        myBuffer.append(" ");
        myBuffer.append(variable);
      }
      myBuffer.append('=');
      startConstructor(componentClass);
      endConstructor(); // will finish the line
    }

    if (component instanceof LwContainer) {
      getComponentLayoutGenerator((LwContainer) component).generateContainerLayout((LwContainer) component, this, variable);
    }

    // introspected properties
    final LwIntrospectedProperty[] introspectedProperties = component.getAssignedIntrospectedProperties();

    // see SCR #35990
    Arrays.sort(introspectedProperties, new Comparator<LwIntrospectedProperty>() {
      public int compare(LwIntrospectedProperty p1, LwIntrospectedProperty p2) {
        return p1.getName().compareTo(p2.getName());
      }
    });

    for (final LwIntrospectedProperty property : introspectedProperties) {
      if (property instanceof LwIntroComponentProperty) {
        // component properties are processed in second pass
        continue;
      }

      Object value = component.getPropertyValue(property);

      //noinspection HardCodedStringLiteral
      final boolean isTextWithMnemonicProperty =
        "text".equals(property.getName()) &&
        (isAssignableFrom(AbstractButton.class.getName(), componentClass, globalSearchScope) ||
         isAssignableFrom(JLabel.class.getName(), componentClass, globalSearchScope));

      // handle resource bundles
      if (property instanceof LwRbIntroStringProperty) {
        final StringDescriptor descriptor = (StringDescriptor)value;
        if (descriptor.getValue() == null) {
          if (isTextWithMnemonicProperty) {
            if (isAssignableFrom(AbstractButton.class.getName(), componentClass, globalSearchScope)) {
              myNeedLoadButtonText = true;
              startMethodCall("this", AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD);
              pushVar(variable);
              push(descriptor);
              endMethod();
            }
            else {
              myNeedLoadLabelText = true;
              startMethodCall("this", AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD);
              pushVar(variable);
              push(descriptor);
              endMethod();
            }
          }
          else {
            startMethodCall(variable, property.getWriteMethodName());
            push(descriptor);
            endMethod();
          }

          continue;
        }
        else {
          value = descriptor.getValue();
        }
      }
      else if (property instanceof LwIntroListModelProperty) {
        generateListModelProperty(property, class2variableIndex, aClass, value, variable);
        continue;
      }

      SupportCode.TextWithMnemonic textWithMnemonic = null;
      if (isTextWithMnemonicProperty) {
        textWithMnemonic = SupportCode.parseText((String)value);
        value = textWithMnemonic.myText;
      }


      final String propertyClass = property.getPropertyClassName();
      if (propertyClass.equals(Color.class.getName())) {
        ColorDescriptor descriptor = (ColorDescriptor) value;
        if (!descriptor.isColorSet()) continue;
      }

      startMethodCall(variable, property.getWriteMethodName());

      if (propertyClass.equals(Dimension.class.getName())) {
        newDimension((Dimension)value);
      }
      else if (propertyClass.equals(Integer.class.getName())) {
        push(((Integer)value).intValue());
      }
      else if (propertyClass.equals(Double.class.getName())) {
        push(((Double)value).doubleValue());
      }
      else if (propertyClass.equals(Float.class.getName())) {
        push(((Float)value).floatValue());
      }
      else if (propertyClass.equals(Boolean.class.getName())) {
        push(((Boolean)value).booleanValue());
      }
      else if (propertyClass.equals(Rectangle.class.getName())) {
        newRectangle((Rectangle)value);
      }
      else if (propertyClass.equals(Insets.class.getName())) {
        newInsets((Insets)value);
      }
      else if (propertyClass.equals(String.class.getName())) {
        push((String)value);
      }
      else if (propertyClass.equals(Color.class.getName())) {
        pushColor((ColorDescriptor) value);
      }
      else if (propertyClass.equals(Font.class.getName())) {
        pushFont(variable, (FontDescriptor) value);
      }
      else if (propertyClass.equals(Icon.class.getName())) {
        pushIcon((IconDescriptor) value);
      }
      else if (property instanceof LwIntroEnumProperty) {
        pushVar(propertyClass.replace('$', '.') + "." + value.toString());
      }
      else {
        throw new RuntimeException("unexpected property class: " + propertyClass);
      }

      endMethod();

      // special handling of mnemonics

      if (!isTextWithMnemonicProperty) {
        continue;
      }

      if (textWithMnemonic.myMnemonicIndex == -1) {
        continue;
      }

      if (isAssignableFrom(AbstractButton.class.getName(), componentClass, globalSearchScope)) {
        generateSetMnemonic(variable, textWithMnemonic, module, "setMnemonic", AbstractButton.class);
      }
      else if (isAssignableFrom(JLabel.class.getName(), componentClass, globalSearchScope)) {
        generateSetMnemonic(variable, textWithMnemonic, module, "setDisplayedMnemonic", JLabel.class);
      }
    }

    generateClientProperties(component, variable);

    // add component to parent
    if (!(component.getParent() instanceof LwRootContainer)) {

      final String parentVariable = getVariable(parent, component2TempVariable, class2variableIndex, aClass);
      String componentVar = variable;
      if (component instanceof LwNestedForm) {
        componentVar = variable + "." + AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME + "()";
      }
      getComponentLayoutGenerator(component.getParent()).generateComponentLayout(component, this, componentVar, parentVariable);
    }

    if (component instanceof LwContainer) {
      final LwContainer container = (LwContainer)component;

      generateBorder(container, variable);

      for (int i = 0; i < container.getComponentCount(); i++) {
        generateSetupCodeForComponent((LwComponent)container.getComponent(i), component2TempVariable, class2variableIndex, id2component,
                                      module, aClass);
      }
    }
  }

  private void generateSetMnemonic(final String variable, final SupportCode.TextWithMnemonic textWithMnemonic, final Module module,
                                   @NonNls final String setMethodName, final Class controlClass) {
    startMethodCall(variable, setMethodName);
    pushVar("'" + textWithMnemonic.getMnemonicChar() + "'");
    endMethod();

    if (haveSetDisplayedMnemonic(controlClass, module)) {
      // generated code needs to be compatible with jdk 1.3
      startMethodCall(variable, "setDisplayedMnemonicIndex");
      push(textWithMnemonic.myMnemonicIndex);
      endMethod();
    }
  }

  private boolean haveSetDisplayedMnemonic(final Class controlClass, final Module module) {
    PsiClass aClass = PsiManager.getInstance(myProject).findClass(controlClass.getName(), module.getModuleWithLibrariesScope());
    return aClass != null && aClass.findMethodsByName("setDisplayedMnemonicIndex", true).length > 0;
  }

  private void generateListModelProperty(final LwIntrospectedProperty property, final TObjectIntHashMap<String> class2variableIndex,
                                         final PsiClass aClass, final Object value, final String variable) {
    String valueClassName;
    if (property.getPropertyClassName().equals(ComboBoxModel.class.getName())) {
      valueClassName = DefaultComboBoxModel.class.getName();
    }
    else {
      valueClassName = DefaultListModel.class.getName();
    }
    String modelVarName = generateUniqueVariableName(valueClassName, class2variableIndex, aClass);
    myBuffer.append("final ");
    myBuffer.append(valueClassName);
    myBuffer.append(" ");
    myBuffer.append(modelVarName);
    myBuffer.append("= new ").append(valueClassName).append("();");
    String[] items = (String[]) value;
    for(String item: items) {
      startMethodCall(modelVarName, "addElement");
      push(item);
      endMethod();
    }

    startMethodCall(variable, property.getWriteMethodName());
    pushVar(modelVarName);
    endMethod();
  }

  private void generateBorder(final LwContainer container, final String variable) {
    final BorderType borderType = container.getBorderType();
    final StringDescriptor borderTitle = container.getBorderTitle();
    final Insets borderSize = container.getBorderSize();
    final String borderFactoryMethodName = borderType.getBorderFactoryMethodName();

    final boolean borderNone = borderType.equals(BorderType.NONE);
    if (!borderNone || borderTitle != null) {
      startMethodCall(variable, "setBorder");


      startStaticMethodCall(BorderFactory.class, "createTitledBorder");

      if (!borderNone) {
        startStaticMethodCall(BorderFactory.class, borderFactoryMethodName);
        if (borderType.equals(BorderType.LINE)) {
          if (container.getBorderColor() == null) {
            pushVar("Color.black");
          }
          else {
            pushColor(container.getBorderColor());
          }
        }
        else if (borderType.equals(BorderType.EMPTY) && borderSize != null) {
          push(borderSize.top);
          push(borderSize.left);
          push(borderSize.bottom);
          push(borderSize.right);
        }
        endMethod();
      }
      else if (isCustomBorder(container)) {
        push((String) null);
      }

      push(borderTitle);

      if (isCustomBorder(container)) {
        push(container.getBorderTitleJustification(), ourTitleJustificationMap);
        push(container.getBorderTitlePosition(), ourTitlePositionMap);
        if (container.getBorderTitleFont() != null || container.getBorderTitleColor() != null) {
          if (container.getBorderTitleFont() == null) {
            push((String) null);
          }
          else {
            pushFont(variable, container.getBorderTitleFont());
          }
          if (container.getBorderTitleColor() != null) {
            pushColor(container.getBorderTitleColor());
          }
        }
      }

      endMethod(); // createTitledBorder

      endMethod(); // setBorder
    }
  }

  private static boolean isCustomBorder(final LwContainer container) {
    return container.getBorderTitleJustification() != 0 || container.getBorderTitlePosition() != 0 ||
           container.getBorderTitleColor() != null || container.getBorderTitleFont() != null;
  }

  private void generateClientProperties(final LwComponent component, final String variable) throws CodeGenerationException {
    HashMap props = component.getDelegeeClientProperties();
    for (final Object o : props.entrySet()) {
      Map.Entry e = (Map.Entry)o;
      startMethodCall(variable, "putClientProperty");
      push((String) e.getKey());

      Object value = e.getValue();
      if (value instanceof StringDescriptor) {
        push(((StringDescriptor) value).getValue());
      }
      else if (value instanceof Boolean) {
        if (((Boolean) value).booleanValue()) {
          pushVar("Boolean.TRUE");
        }
        else {
          pushVar("Boolean.FALSE");
        }
      }
      else {
        startConstructor(value.getClass().getName());
        if (value instanceof Integer) {
          push(((Integer) value).intValue());
        }
        else if (value instanceof Double) {
          push(((Double) value).doubleValue());
        }
        else {
          throw new CodeGenerationException(component.getId(), "Unknown client property value type");
        }
        endConstructor();
      }

      endMethod();
    }
  }

  private static String getNestedFormClass(Module module, final LwNestedForm nestedForm) throws CodeGenerationException {
    final LwRootContainer container;
    try {
      container = new PsiNestedFormLoader(module).loadForm(nestedForm.getFormFileName());
      return container.getClassToBind();
    }
    catch (Exception e) {
      throw new CodeGenerationException(nestedForm.getId(), e.getMessage());
    }
  }

  private void generateComponentReferenceProperties(final LwComponent component,
                                                    final HashMap<LwComponent, String> component2variable,
                                                    final TObjectIntHashMap<String> class2variableIndex,
                                                    final HashMap<String, LwComponent> id2component,
                                                    final PsiClass aClass) {
    String variable = getVariable(component, component2variable, class2variableIndex, aClass);
    final LwIntrospectedProperty[] introspectedProperties = component.getAssignedIntrospectedProperties();
    for (final LwIntrospectedProperty property : introspectedProperties) {
      if (property instanceof LwIntroComponentProperty) {
        String componentId = (String) component.getPropertyValue(property);
        if (componentId != null && componentId.length() > 0) {
          LwComponent target = id2component.get(componentId);
          if (target != null) {
            String targetVariable = getVariable(target, component2variable, class2variableIndex, aClass);
            startMethodCall(variable, property.getWriteMethodName());
            pushVar(targetVariable);
            endMethod();
          }
        }
      }
    }

    if (component instanceof LwContainer) {
      final LwContainer container = (LwContainer)component;
      for (int i = 0; i < container.getComponentCount(); i++) {
        generateComponentReferenceProperties((LwComponent)container.getComponent(i), component2variable, class2variableIndex, id2component,
                                             aClass);
      }
    }
  }

  private void generateButtonGroups(final LwRootContainer rootContainer,
                                    final HashMap<LwComponent, String> component2variable,
                                    final TObjectIntHashMap<String> class2variableIndex,
                                    final HashMap<String, LwComponent> id2component,
                                    final PsiClass aClass) {
    IButtonGroup[] groups = rootContainer.getButtonGroups();
    boolean haveGroupDeclaration = false;
    for(IButtonGroup group: groups) {
      boolean haveGroupConstructor = false;
      String[] ids = group.getComponentIds();
      for(String id: ids) {
        LwComponent target = id2component.get(id);
        if (target != null) {
          if (!haveGroupConstructor) {
            if (group.isBound()) {
              append(group.getName());
            }
            else {
              if (!haveGroupDeclaration) {
                append("javax.swing.ButtonGroup buttonGroup;");
                haveGroupDeclaration = true;
              }
              append("buttonGroup");
            }
            append("= new javax.swing.ButtonGroup();");
            haveGroupConstructor = true;
          }
          String targetVariable = getVariable(target, component2variable, class2variableIndex, aClass);
          startMethodCall(group.isBound() ? group.getName() : "buttonGroup", "add");
          pushVar(targetVariable);
          endMethod();
        }
      }
    }
  }

  private static LayoutSourceGenerator getComponentLayoutGenerator(final LwContainer container) {
    LayoutSourceGenerator generator = ourComponentLayoutCodeGenerators.get(container.getClass());
    if (generator != null) {
      return generator;
    }
    LwContainer parent = container;
    while(parent != null) {
      final String layoutManager = parent.getLayoutManager();
      if (layoutManager != null && layoutManager.length() > 0) {
        generator = ourContainerLayoutCodeGenerators.get(layoutManager);
        if (generator != null) {
          return generator;
        }
      }
      parent = parent.getParent();
    }
    return GridLayoutSourceGenerator.INSTANCE;
  }

  void push(final StringDescriptor descriptor) {
    if (descriptor == null) {
      push((String)null);
    }
    else if (descriptor.getValue() != null) {
      push(descriptor.getValue());
    }
    else {
      startMethodCall("java.util.ResourceBundle.getBundle(\"" + descriptor.getBundleName() + "\")", "getString");
      push(descriptor.getKey());
      endMethod();
    }
  }

  private void pushColor(final ColorDescriptor descriptor) {
    if (descriptor.getColor() != null) {
      startConstructor(Color.class.getName());
      push(descriptor.getColor().getRGB());
      endConstructor();
    }
    else if (descriptor.getSwingColor() != null) {
      startStaticMethodCall(UIManager.class, "getColor");
      push(descriptor.getSwingColor());
      endMethod();
    }
    else if (descriptor.getSystemColor() != null) {
      pushVar("java.awt.SystemColor." + descriptor.getSystemColor());
    }
    else if (descriptor.getAWTColor() != null) {
      pushVar("java.awt.Color." + descriptor.getAWTColor());
    }
    else {
      throw new IllegalStateException("Unknown color type");
    }
  }

  private void pushFont(final String variable, final FontDescriptor fontDescriptor) {
    if (fontDescriptor.getSwingFont() != null) {
      startStaticMethodCall(UIManager.class, "getFont");
      push(fontDescriptor.getSwingFont());
      endMethod();
    }
    else {
      startConstructor(Font.class.getName());
      if (fontDescriptor.getFontName() != null) {
        push(fontDescriptor.getFontName());
      }
      else {
        pushVar(variable + ".getFont().getName()");
      }
      if (fontDescriptor.getFontStyle() >= 0) {
        push(fontDescriptor.getFontStyle(), ourFontStyleMap);
      }
      else {
        pushVar(variable + ".getFont().getStyle()");
      }
      if (fontDescriptor.getFontSize() >= 0) {
        push(fontDescriptor.getFontSize());
      }
      else {
        pushVar(variable + ".getFont().getSize()");
      }
      endMethod();
    }
  }

  public void pushIcon(final IconDescriptor iconDescriptor) {
    startConstructor(ImageIcon.class.getName());
    startMethodCall("getClass()", "getResource");
    push("/" + iconDescriptor.getIconPath());
    endMethod();
    endMethod();
  }

  private boolean isAssignableFrom(final String className, final String fromName, final GlobalSearchScope scope) {
    final PsiClass aClass = PsiManager.getInstance(myProject).findClass(className, scope);
    final PsiClass fromClass = PsiManager.getInstance(myProject).findClass(fromName, scope);
    if (aClass == null || fromClass == null) {
      return false;
    }
    return InheritanceUtil.isInheritorOrSelf(fromClass, aClass, true);
  }

  /**
   * @return variable idx
   */
  private static String getVariable(final LwComponent component,
                                    final HashMap<LwComponent, String> component2variable,
                                    final TObjectIntHashMap<String> class2variableIndex,
                                    final PsiClass aClass) {
    if (component2variable.containsKey(component)) {
      return component2variable.get(component);
    }

    if (component.getBinding() != null) {
      return component.getBinding();
    }

    @NonNls final String className = component instanceof LwNestedForm ? "nestedForm" : component.getComponentClassName();

    String result = generateUniqueVariableName(className, class2variableIndex, aClass);
    component2variable.put(component, result);

    return result;
  }

  private static String generateUniqueVariableName(@NonNls final String className, final TObjectIntHashMap<String> class2variableIndex,
                                                   final PsiClass aClass) {
    final String shortName;
    if (className.startsWith("javax.swing.J")) {
      shortName = className.substring("javax.swing.J".length());
    }
    else {
      final int idx = className.lastIndexOf('.');
      if (idx != -1) {
        shortName = className.substring(idx + 1);
      }
      else {
        shortName = className;
      }
    }

    if (!class2variableIndex.containsKey(className)) class2variableIndex.put(className, 0);
    String result;
    do {
      class2variableIndex.increment(className);
      int newIndex = class2variableIndex.get(className);

      result = Character.toLowerCase(shortName.charAt(0)) + shortName.substring(1) + newIndex;
    } while(aClass.findFieldByName(result, true) != null);
    return result;
  }

  void newDimensionOrNull(final Dimension dimension) {
    if (dimension.width == -1 && dimension.height == -1) {
      checkParameter();
      myBuffer.append("null");
    }
    else {
      newDimension(dimension);
    }
  }

  void newDimension(final Dimension dimension) {
    startConstructor(Dimension.class.getName());
    push(dimension.width);
    push(dimension.height);
    endConstructor();
  }

  void newInsets(final Insets insets){
    startConstructor(Insets.class.getName());
    push(insets.top);
    push(insets.left);
    push(insets.bottom);
    push(insets.right);
    endConstructor();
  }

  private void newRectangle(final Rectangle rectangle) {
    startConstructor(Rectangle.class.getName());
    push(rectangle.x);
    push(rectangle.y);
    push(rectangle.width);
    push(rectangle.height);
    endConstructor();
  }


  void startMethodCall(@NonNls final String variable, @NonNls final String methodName) {
    checkParameter();

    append(variable);
    myBuffer.append('.');
    myBuffer.append(methodName);
    myBuffer.append('(');

    myIsFirstParameterStack.push(Boolean.TRUE);
  }

  private void startStaticMethodCall(final Class aClass, @NonNls final String methodName) {
    checkParameter();

    myBuffer.append(aClass.getName());
    myBuffer.append('.');
    myBuffer.append(methodName);
    myBuffer.append('(');

    myIsFirstParameterStack.push(Boolean.TRUE);
  }

  void endMethod() {
    myBuffer.append(')');

    myIsFirstParameterStack.pop();

    if (myIsFirstParameterStack.empty()) {
      myBuffer.append(";\n");
    }
  }

  void startConstructor(final String className) {
    checkParameter();

    myBuffer.append("new ");
    myBuffer.append(className);
    myBuffer.append('(');

    myIsFirstParameterStack.push(Boolean.TRUE);
  }

  void endConstructor() {
    endMethod();
  }

  void push(final int value) {
    checkParameter();
    append(value);
  }

  void append(final int value) {
    myBuffer.append(value);
  }

  void push(final int value, final TIntObjectHashMap map){
    final String stringRepresentation = (String)map.get(value);
    if (stringRepresentation != null) {
      checkParameter();
      myBuffer.append(stringRepresentation);
    }
    else {
      push(value);
    }
  }

  private void push(final double value) {
    checkParameter();
    append(value);
  }

  public void append(final double value) {
    myBuffer.append(value);
  }

  private void push(final float value) {
    checkParameter();
    append(value);
  }

  public void append(final float value) {
    myBuffer.append(value).append("f");
  }

  void push(final boolean value) {
    checkParameter();
    myBuffer.append(value);
  }

  void push(final String value) {
    checkParameter();
    if (value == null) {
      myBuffer.append("null");
    }
    else {
      myBuffer.append('"');
      myBuffer.append(StringUtil.escapeStringCharacters(value));
      myBuffer.append('"');
    }
  }

  void pushVar(@NonNls final String variable) {
    checkParameter();
    append(variable);
  }

  void append(@NonNls final String text) {
    myBuffer.append(text);
  }

  void checkParameter() {
    if (!myIsFirstParameterStack.empty()) {
      final Boolean b = myIsFirstParameterStack.pop();
      if (b.equals(Boolean.FALSE)) {
        myBuffer.append(',');
      }
      myIsFirstParameterStack.push(Boolean.FALSE);
    }
  }
}
