// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.make;

import com.intellij.DynamicBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.uiDesigner.*;
import com.intellij.uiDesigner.compiler.*;
import com.intellij.uiDesigner.core.SupportCode;
import com.intellij.uiDesigner.lw.*;
import com.intellij.uiDesigner.shared.BorderType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.BooleanStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public final class FormSourceCodeGenerator {
  private static final Logger LOG = Logger.getInstance(FormSourceCodeGenerator.class);

  private @NonNls StringBuffer myBuffer;
  private BooleanStack myIsFirstParameterStack;
  private final Project myProject;
  private final ArrayList<FormErrorInfo> myErrors;
  private boolean myNeedLoadLabelText;
  private boolean myNeedLoadButtonText;

  private static final Map<Class<?>, LayoutSourceGenerator> ourComponentLayoutCodeGenerators = new HashMap<>();
  private static final Map<String, LayoutSourceGenerator> ourContainerLayoutCodeGenerators = new HashMap<>();
  private static final @NonNls Int2ObjectMap<String> ourFontStyleMap = new Int2ObjectOpenHashMap<>();
  private static final @NonNls Int2ObjectMap<String> ourTitleJustificationMap = new Int2ObjectOpenHashMap<>();
  private static final @NonNls Int2ObjectMap<String> ourTitlePositionMap = new Int2ObjectOpenHashMap<>();

  private boolean myNeedGetMessageFromBundle;

  private static final ElementPattern<PsiExpressionStatement> ourSuperCallPattern =
    PsiJavaPatterns.psiExpressionStatement().withFirstChild(
      PlatformPatterns.psiElement(PsiMethodCallExpression.class).withFirstChild(PlatformPatterns.psiElement().withText(JavaKeywords.SUPER)));

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

  public FormSourceCodeGenerator(final @NotNull Project project){
    myProject = project;
    myErrors = new ArrayList<>();
  }

  public void generate(final VirtualFile formFile) {
    myNeedLoadLabelText = false;
    myNeedLoadButtonText = false;
    myNeedGetMessageFromBundle = false;
    myGetFontMethod = null;

    final Module module = ModuleUtilCore.findModuleForFile(formFile, myProject);
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
    if (doc == null) {
      LOG.warn("Unable to get document for " + formFile.getPath());
      return;
    }

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
        @Override
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

    if (!myErrors.isEmpty()) {
      return;
    }

    try {
      _generate(rootContainer, module);
    }
    catch (ClassToBindNotFoundException e) {
      // ignore
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
    myIsFirstParameterStack = new BooleanStack();

    final HashMap<LwComponent,String> component2variable = new HashMap<>();
    final Object2IntMap<String> class2variableIndex = new Object2IntOpenHashMap<>();
    final HashMap<String,LwComponent> id2component = new HashMap<>();

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

    final boolean haveCustomCreateComponents = Utils.getCustomCreateComponentCount(rootContainer) > 0;
    if (haveCustomCreateComponents) {
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
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();

    PsiClass newClass = (PsiClass) classToBind.copy();

    PsiElement initializerComment = null;
    PsiElement firstMethodMarker = null;
    PsiMethod[] methods = newClass.findMethodsByName(AsmCodeGenerator.SETUP_METHOD_NAME, false);
    PsiClassInitializer[] initializers = newClass.getInitializers();

    for (PsiMethod method: methods) {
      if (method.hasParameters()) {
        continue;
      }
      for (PsiClassInitializer initializer : initializers) {
        if (containsMethodIdentifier(initializer, method)) {
          PsiElement child = initializer.getFirstChild();
          // very smart java PSI move comment node before class initializer to initializer children
          if (child instanceof PsiComment && child.getTextOffset() == initializer.getTextOffset()) {
            initializerComment = child.copy();
            break;
          }
        }
      }
      firstMethodMarker = newClass
        .addBefore(elementFactory.createFieldFromText("private int ____temp_field__" + System.currentTimeMillis() + ";", null), method);
      break;
    }

    cleanup(newClass);

    // [anton] the comments are written according to the SCR 26896
    final PsiClass fakeClass = elementFactory.createClassFromText(
      "{\n" +
      "// GUI initializer generated by " + ApplicationNamesInfo.getInstance().getFullProductName() + " GUI Designer\n" +
      "// >>> IMPORTANT!! <<<\n" +
      "// DO NOT EDIT OR ADD ANY CODE HERE!\n" +
      AsmCodeGenerator.SETUP_METHOD_NAME + "();\n" +
      "}\n" +
      "\n" +
      "/** Method generated by " + ApplicationNamesInfo.getInstance().getFullProductName() + " GUI Designer\n" +
      " * >>> IMPORTANT!! <<<\n" +
      " * DO NOT edit this method OR call it in your code!\n" +
      " * @noinspection ALL\n" +
      " */\n" +
      "private void " + AsmCodeGenerator.SETUP_METHOD_NAME + "()\n" +
      "{\n" +
      methodText +
      "}\n",
      null
    );

    final CodeStyleManager formatter = CodeStyleManager.getInstance(module.getProject());
    final JavaCodeStyleManager styler = JavaCodeStyleManager.getInstance(module.getProject());

    PsiMethod method = (PsiMethod)(firstMethodMarker == null
                                   ? newClass.add(fakeClass.getMethods()[0])
                                   : newClass.addAfter(fakeClass.getMethods()[0], firstMethodMarker));
    if (firstMethodMarker != null) {
      firstMethodMarker.delete();
    }

    // don't generate initializer block if $$$setupUI$$$() is called explicitly from one of the constructors
    boolean needInitializer = true;
    boolean needSetupUI = false;
    for(PsiMethod constructor: newClass.getConstructors()) {
      if (containsMethodIdentifier(constructor, method)) {
        needInitializer = false;
      }
      else if (haveCustomCreateComponents && hasCustomComponentAffectingReferences(constructor, newClass, rootContainer, null)) {
        needInitializer = false;
        needSetupUI = true;
      }
    }

    if (needSetupUI) {
      for(PsiMethod constructor: newClass.getConstructors()) {
        addSetupUICall(constructor, rootContainer, method);
      }
    }

    if (needInitializer) {
      PsiElement initializer = newClass.addBefore(fakeClass.getInitializers()[0], method);
      if (initializerComment != null) {
        newClass.addBefore(initializerComment, initializer);
      }
    }

    final @NonNls String grcMethodText = "/** @noinspection ALL */ public javax.swing.JComponent " +
                                         AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME +
                                         "() { return " + topComponent.getBinding() + "; }";
    generateMethodIfRequired(newClass, method, AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME, grcMethodText, topComponent.getBinding() != null);

    final String loadButtonTextMethodText = getLoadMethodText(AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD, AbstractButton.class, module);
    generateMethodIfRequired(newClass, method, AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD, loadButtonTextMethodText, myNeedLoadButtonText);
    final String loadLabelTextMethodText = getLoadMethodText(AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD, JLabel.class, module);
    generateMethodIfRequired(newClass, method, AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD, loadLabelTextMethodText, myNeedLoadLabelText);

    generateGetMessageFromBundle(newClass, method, elementFactory, myNeedGetMessageFromBundle);

    if (myGetFontMethod != null) {
      String getFontMethod =
        "/** @noinspection ALL */ " +
        "private java.awt.Font " + myGetFontMethod +
        "(java.lang.String fontName, int style, int size, java.awt.Font currentFont) {" +
        "if (currentFont == null) return null;" +
        "java.lang.String resultName;" +
        "if (fontName == null) {resultName = currentFont.getName();}" +
        "else {" +
        "  java.awt.Font testFont = new java.awt.Font(fontName, java.awt.Font.PLAIN, 10);" +
        "  if (testFont.canDisplay('a') && testFont.canDisplay('1')) {resultName = fontName;}" +
        "  else {resultName = currentFont.getName();}" +
        "}" +
        "java.awt.Font font = new java.awt.Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());" +
        "boolean isMac = java.lang.System.getProperty(\"os.name\", \"\").toLowerCase(java.util.Locale.ENGLISH).startsWith(\"mac\");" +
        "java.awt.Font fontWithFallback = isMac" +
        "? new java.awt.Font(font.getFamily(), font.getStyle(), font.getSize())" +
        ": new javax.swing.text.StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());" +
        "return fontWithFallback instanceof javax.swing.plaf.FontUIResource ? fontWithFallback : new javax.swing.plaf.FontUIResource(fontWithFallback);}";

      generateMethodIfRequired(newClass, method, myGetFontMethod, getFontMethod, true);
    }

    newClass = (PsiClass) styler.shortenClassReferences(newClass);
    newClass = (PsiClass) formatter.reformat(newClass);

    if (!lexemsEqual(classToBind, newClass)) {
      classToBind.replace(newClass);
    }
  }

  private static void addSetupUICall(final PsiMethod constructor, final LwRootContainer rootContainer, final PsiMethod setupUIMethod) {
    final PsiCodeBlock psiCodeBlock = constructor.getBody();
    if (psiCodeBlock == null) {
      return;
    }
    final PsiClass classToBind = constructor.getContainingClass();
    final PsiStatement[] statements = psiCodeBlock.getStatements();
    PsiElement anchor = psiCodeBlock.getRBrace();
    Ref<Boolean> callsThisConstructor = new Ref<>(Boolean.FALSE);
    for(PsiStatement statement: statements) {
      if (containsMethodIdentifier(statement, setupUIMethod)) {
        return;
      }
      if (!ourSuperCallPattern.accepts(statement) &&
          hasCustomComponentAffectingReferences(statement, classToBind, rootContainer, callsThisConstructor)) {
        anchor = statement;
        break;
      }
    }
    if (!callsThisConstructor.get().booleanValue()) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(constructor.getProject()).getElementFactory();
      try {
        PsiStatement setupUIStatement = factory.createStatementFromText(AsmCodeGenerator.SETUP_METHOD_NAME + "();", constructor);
        psiCodeBlock.addBefore(setupUIStatement, anchor);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private static boolean hasCustomComponentAffectingReferences(final PsiElement element,
                                                               final PsiClass classToBind,
                                                               final LwRootContainer rootContainer,
                                                               final @Nullable Ref<? super Boolean> callsThisConstructor) {
    final Ref<Boolean> result = new Ref<>(Boolean.FALSE);
    element.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override public void visitReferenceExpression(final @NotNull PsiReferenceExpression expression) {
        super.visitReferenceElement(expression);
        final PsiElement psiElement = expression.resolve();
        if (psiElement == null) {
          return;
        }
        if (psiElement instanceof PsiField field) {
          if (field.getContainingClass() == classToBind) {
            if (Utils.isBoundField(rootContainer, field.getName())) {
              result.set(Boolean.TRUE);
            }
          }
        }
        else if (psiElement instanceof PsiMethod method) {
          if (method.isConstructor()) {
            if (method.getContainingClass() == classToBind) {
              if (callsThisConstructor != null) {
                callsThisConstructor.set(Boolean.TRUE);
              }
            }
            else if (method.getContainingClass() != classToBind.getSuperClass()) {
              result.set(Boolean.TRUE);
            }
          }
          else {
            result.set(Boolean.TRUE);
          }
        }
      }
    });

    return result.get().booleanValue();
  }

  private static boolean lexemsEqual(final PsiClass classToBind, final PsiClass newClass) {
    Lexer oldTextLexer = JavaParserDefinition.createLexer(LanguageLevel.HIGHEST);
    Lexer newTextLexer = JavaParserDefinition.createLexer(LanguageLevel.HIGHEST);
    String oldBuffer = classToBind.getText();
    String newBuffer = newClass.getText();
    oldTextLexer.start(oldBuffer);
    newTextLexer.start(newBuffer);

    while(true) {
      IElementType oldLexem = oldTextLexer.getTokenType();
      IElementType newLexem = newTextLexer.getTokenType();
      if (oldLexem == null || newLexem == null) {
        // must terminate at the same time
        return oldLexem == null && newLexem == null;
      }
      if (oldLexem != newLexem) {
        return false;
      }
      if (oldLexem != TokenType.WHITE_SPACE && oldLexem != JavaDocElementType.DOC_COMMENT) {
        int oldStart = oldTextLexer.getTokenStart();
        int newStart = newTextLexer.getTokenStart();
        int oldLength = oldTextLexer.getTokenEnd() - oldTextLexer.getTokenStart();
        int newLength = newTextLexer.getTokenEnd() - newTextLexer.getTokenStart();
        if (oldLength != newLength) {
          return false;
        }
        for(int i=0; i<oldLength; i++) {
          if (oldBuffer.charAt(oldStart+i) != newBuffer.charAt(newStart+i)) {
            return false;
          }
        }
      }
      oldTextLexer.advance();
      newTextLexer.advance();
    }
  }

  private @NonNls String getLoadMethodText(final String methodName, final Class<?> componentClass, Module module) {
    final boolean needIndex = haveSetDisplayedMnemonic(componentClass, module);
    return
      "/** @noinspection ALL */ " +
      "private void " + methodName + "(" + componentClass.getName() + " component, java.lang.String text) {" +
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

  /* Generates

  private static Method $$$cachedGetBundleMethod$$$ = null;

  private String $$$getMessageFromBundle$$$(String path, String key) {
    java.util.ResourceBundle bundle;

    try {
      Class<?> thisClass = this.getClass();
      if ($$$cachedGetBundleMethod$$$ == null) {
        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
        $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
      }
      bundle = (java.util.ResourceBundle) $$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
    } catch (Exception e) {
      bundle =  java.util.ResourceBundle.getBundle(path);
    }

    return bundle.getString(key);
  }
  */
  private static void generateGetMessageFromBundle(PsiClass aClass, PsiMethod anchor, PsiElementFactory elementFactory, boolean condition) {
    String dynamicBundleClassName = DynamicBundle.class.getName();

    String methodName = AsmCodeGenerator.GET_MESSAGE_FROM_BUNDLE;
    for (PsiMethod oldMethod : aClass.findMethodsByName(methodName, false)) {
      oldMethod.delete();
    }

    String fieldName = AsmCodeGenerator.CACHED_GET_BUNDLE_METHOD;
    PsiField oldField = aClass.findFieldByName(fieldName, false);
    if (oldField != null) {
      oldField.delete();
    }

    if (!condition) {
      return;
    }

    PsiField cachedMethodField =
      elementFactory.createFieldFromText(
        "private static java.lang.reflect.Method " + fieldName + " = null;",
        null);

    String methodText =
      "private String " + methodName + "(String path, String key) {\n" +
      " java.util.ResourceBundle bundle;\n" +
      "try {\n" +
      "    Class<?> thisClass = this.getClass();\n" +
      "    if (" + fieldName + " == null) {\n" +
      "        Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass(\"" + dynamicBundleClassName + "\");\n" +
      "        " + fieldName + " = dynamicBundleClass.getMethod(\"getBundle\", String.class, Class.class);\n" +
      "    }\n" +
      "    bundle = (java.util.ResourceBundle)" + fieldName + ".invoke(null, path, thisClass);\n" +
      "} catch (Exception e) {\n" +
      "    bundle = java.util.ResourceBundle.getBundle(path);\n" +
      "}\n" +
      "return bundle.getString(key);\n" +
      "}";

    PsiMethod method = elementFactory.createMethodFromText(methodText, aClass);

    aClass.addAfter(method, anchor);
    aClass.addAfter(cachedMethodField, anchor);
  }

  private void generateMethodIfRequired(PsiClass aClass, PsiMethod anchor, final String methodName, String methodText, boolean condition) throws IncorrectOperationException {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
    PsiMethod[] oldMethods = aClass.findMethodsByName(methodName, false);
    if (!condition) {
      for(PsiMethod oldMethod: oldMethods) {
        oldMethod.delete();
      }
    }
    else {
      PsiMethod newMethod = elementFactory.createMethodFromText(methodText, aClass);
      if (oldMethods.length > 0) {
        oldMethods[0].replace(newMethod);
      }
      else {
        aClass.addAfter(newMethod, anchor);
      }
    }
  }

  public static void cleanup(final PsiClass aClass) throws IncorrectOperationException {
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
    deleteMethods(aClass, AsmCodeGenerator.GET_FONT_METHOD_NAME);
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
                                             final Object2IntMap<String> class2variableIndex,
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

    if (component instanceof LwContainer container) {
      if (!container.isCustomCreate() || container.getComponentCount() > 0) {
        getComponentLayoutGenerator(container).generateContainerLayout(container, this, variable);
      }
    }

    // introspected properties
    final LwIntrospectedProperty[] introspectedProperties = component.getAssignedIntrospectedProperties();

    // see SCR #35990
    Arrays.sort(introspectedProperties, Comparator.comparing(p -> p.getName()));

    for (final LwIntrospectedProperty property : introspectedProperties) {
      if (property instanceof LwIntroComponentProperty) {
        // component properties are processed in second pass
        continue;
      }

      Object value = component.getPropertyValue(property);

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
            }
            else {
              myNeedLoadLabelText = true;
              startMethodCall("this", AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD);
            }
            pushVar(variable);
          }
          else {
            startMethodCall(variable, property.getWriteMethodName());
          }
          push(descriptor);
          endMethod();

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
        //noinspection DataFlowIssue
        ColorDescriptor descriptor = (ColorDescriptor) value;
        if (!descriptor.isColorSet()) continue;
      }

      if (propertyClass.equals(Font.class.getName())) {
        //noinspection DataFlowIssue
        pushFontProperty(variable, (FontDescriptor) value, property.getReadMethodName(), property.getWriteMethodName());
        continue;
      }

      startMethodCall(variable, property.getWriteMethodName());

      if (propertyClass.equals(Dimension.class.getName())) {
        //noinspection DataFlowIssue
        newDimension((Dimension)value);
      }
      else if (propertyClass.equals(Integer.class.getName())) {
        //noinspection DataFlowIssue
        push(((Integer)value).intValue());
      }
      else if (propertyClass.equals(Double.class.getName())) {
        //noinspection DataFlowIssue
        push(((Double)value).doubleValue());
      }
      else if (propertyClass.equals(Float.class.getName())) {
        //noinspection DataFlowIssue
        push(((Float)value).floatValue());
      }
      else if (propertyClass.equals(Long.class.getName())) {
        //noinspection DataFlowIssue
        push(((Long) value).longValue());
      }
      else if (propertyClass.equals(Short.class.getName())) {
        //noinspection DataFlowIssue
        push(((Short) value).shortValue());
      }
      else if (propertyClass.equals(Byte.class.getName())) {
        //noinspection DataFlowIssue
        push(((Byte) value).byteValue());
      }
      else if (propertyClass.equals(Character.class.getName())) {
        //noinspection DataFlowIssue
        push(((Character) value).charValue());
      }
      else if (propertyClass.equals(Boolean.class.getName())) {
        //noinspection DataFlowIssue
        push(((Boolean)value).booleanValue());
      }
      else if (propertyClass.equals(Rectangle.class.getName())) {
        //noinspection DataFlowIssue
        newRectangle((Rectangle)value);
      }
      else if (propertyClass.equals(Insets.class.getName())) {
        //noinspection DataFlowIssue
        newInsets((Insets)value);
      }
      else if (propertyClass.equals(String.class.getName())) {
        push((String)value);
      }
      else if (propertyClass.equals(Color.class.getName())) {
        //noinspection DataFlowIssue
        pushColor((ColorDescriptor) value);
      }
      else if (propertyClass.equals(Icon.class.getName())) {
        //noinspection DataFlowIssue
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

    if (component instanceof LwContainer container) {

      generateBorder(container, variable, globalSearchScope);

      for (int i = 0; i < container.getComponentCount(); i++) {
        generateSetupCodeForComponent((LwComponent)container.getComponent(i), component2TempVariable, class2variableIndex, id2component,
                                      module, aClass);
      }
    }
  }

  private void generateSetMnemonic(final String variable, final SupportCode.TextWithMnemonic textWithMnemonic, final Module module,
                                   final @NonNls String setMethodName, final Class<?> controlClass) {
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

  private boolean haveSetDisplayedMnemonic(final Class<?> controlClass, final Module module) {
    PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(controlClass.getName(), module.getModuleWithLibrariesScope());
    return aClass != null && aClass.findMethodsByName("setDisplayedMnemonicIndex", true).length > 0;
  }

  private void generateListModelProperty(final LwIntrospectedProperty property,
                                         final Object2IntMap<String> class2variableIndex,
                                         final PsiClass aClass,
                                         final Object value,
                                         final String variable) {
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

  private void generateBorder(final LwContainer container,
                              final String variable,
                              final GlobalSearchScope scope) {
    final BorderType borderType = container.getBorderType();
    final StringDescriptor borderTitle = container.getBorderTitle();
    final Insets borderSize = container.getBorderSize();
    final String borderFactoryMethodName = borderType.getBorderFactoryMethodName();

    final boolean borderNone = borderType.equals(BorderType.NONE);
    if (!borderNone || borderTitle != null) {
      startMethodCall(variable, "setBorder");

      String borderFactoryName = borderFactoryClassName(container, borderTitle, scope);

      startStaticMethodCall(borderFactoryName, "createTitledBorder");

      if (!borderNone) {
        pushBorderFactoryMethod(container, borderType, borderSize, borderFactoryMethodName);
      }
      else {
        push((String) null);
      }
      pushBorderProperties(container, variable, borderTitle);

      endMethod(); // createTitledBorder
      endMethod(); // setBorder
    }
  }

  private @NotNull String borderFactoryClassName(LwContainer container, StringDescriptor borderTitle, GlobalSearchScope scope) {
    StringDescriptor titledBorderFactoryDescriptor =
      (StringDescriptor)container.getDelegeeClientProperties().get(AsmCodeGenerator.ourBorderFactoryClientProperty);

    boolean useIdeBorderFactory =
      borderTitle != null && Boolean.valueOf(System.getProperty("idea.is.internal")).booleanValue();

    if (titledBorderFactoryDescriptor == null && useIdeBorderFactory) {
      titledBorderFactoryDescriptor  = findIdeBorderFactoryClass(scope);
      container.getDelegeeClientProperties().put(AsmCodeGenerator.ourBorderFactoryClientProperty, titledBorderFactoryDescriptor);
    }

    String titledBorderFactoryDescriptorValue = getStringDescriptionValue(titledBorderFactoryDescriptor);
    boolean isCustomFactory = titledBorderFactoryDescriptorValue != null && !titledBorderFactoryDescriptorValue.isEmpty();

    return isCustomFactory ?
           titledBorderFactoryDescriptorValue.replace('$', '.') :
           BorderFactory.class.getName();
  }

  @Nullable
  private static String getStringDescriptionValue(@Nullable StringDescriptor descriptor) {
    return descriptor == null ? null : descriptor.getValue();
  }

  private @Nullable StringDescriptor findIdeBorderFactoryClass(GlobalSearchScope scope) {
    String ideBorderFactoryName = "com.intellij.ui.IdeBorderFactory";
    PsiClass ideBorderFactoryClass = JavaPsiFacade.getInstance(myProject).findClass(ideBorderFactoryName, scope);
    if (ideBorderFactoryClass != null || ApplicationManager.getApplication().isUnitTestMode()) {
      return StringDescriptor.create(ideBorderFactoryName + "$PlainSmallWithIndent");
    }
    return null;
  }

  private void pushBorderProperties(LwContainer container, String variable, StringDescriptor borderTitle) {
    push(borderTitle);
    push(container.getBorderTitleJustification(), ourTitleJustificationMap);
    push(container.getBorderTitlePosition(), ourTitlePositionMap);
    pushFont(container, variable);
    pushColor(container);
  }

  private void pushColor(LwContainer container) {
    if (container.getBorderTitleColor() == null) {
      push((String) null);
    }
    else {
      pushColor(container.getBorderTitleColor());
    }
  }

  private void pushFont(LwContainer container, String variable) {
    if (container.getBorderTitleFont() == null) {
      push((String) null);
    }
    else {
      pushFont(variable, container.getBorderTitleFont(), "getFont");
    }
  }

  private void pushBorderFactoryMethod(LwContainer container, BorderType borderType, Insets borderSize, String borderFactoryMethodName) {
    startStaticMethodCall(BorderFactory.class, borderFactoryMethodName);
    if (borderType.equals(BorderType.LINE)) {
      if (container.getBorderColor() == null) {
        pushVar("java.awt.Color.black");
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

  private void generateClientProperties(final LwComponent component, final String variable) throws CodeGenerationException {
    HashMap<String, Object> props = component.getDelegeeClientProperties();
    for (final Map.Entry<String, Object> e : props.entrySet()) {
      startMethodCall(variable, "putClientProperty");
      push(e.getKey());

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
                                                    final Object2IntMap<String> class2variableIndex,
                                                    final HashMap<String, LwComponent> id2component,
                                                    final PsiClass aClass) {
    String variable = getVariable(component, component2variable, class2variableIndex, aClass);
    final LwIntrospectedProperty[] introspectedProperties = component.getAssignedIntrospectedProperties();
    for (final LwIntrospectedProperty property : introspectedProperties) {
      if (property instanceof LwIntroComponentProperty) {
        String componentId = (String) component.getPropertyValue(property);
        if (componentId != null && !componentId.isEmpty()) {
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

    if (component instanceof LwContainer container) {
      for (int i = 0; i < container.getComponentCount(); i++) {
        generateComponentReferenceProperties((LwComponent)container.getComponent(i), component2variable, class2variableIndex, id2component,
                                             aClass);
      }
    }
  }

  private void generateButtonGroups(final LwRootContainer rootContainer,
                                    final HashMap<LwComponent, String> component2variable,
                                    final Object2IntMap<String> class2variableIndex,
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
      if (layoutManager != null && !layoutManager.isEmpty()) {
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
      myNeedGetMessageFromBundle = true;
      startMethodCall("this", AsmCodeGenerator.GET_MESSAGE_FROM_BUNDLE);
      push(descriptor.getBundleName());
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

  private String myGetFontMethod;

  private void pushFontProperty(String variable, FontDescriptor fontDescriptor, String getterName, String setterName) {
    myBuffer.append("java.awt.Font ").append(variable).append("Font = ");
    pushFont(variable, fontDescriptor, getterName);

    myBuffer.append("if (").append(variable).append("Font != null) ").append(variable).append(".").append(setterName).append("(")
            .append(variable).append("Font);\n");
  }

  private void pushFont(final String variable, final FontDescriptor fontDescriptor, final @NonNls String getterName) {
    if (fontDescriptor.getSwingFont() != null) {
      startStaticMethodCall(UIManager.class, "getFont");
      push(fontDescriptor.getSwingFont());
      endMethod();
    }
    else {
      if (myGetFontMethod == null) {
        myGetFontMethod = AsmCodeGenerator.GET_FONT_METHOD_NAME;
      }

      startMethodCall("this", myGetFontMethod);
      push(fontDescriptor.getFontName());
      push(fontDescriptor.getFontStyle(), ourFontStyleMap);
      push(fontDescriptor.getFontSize());
      pushVar(variable + "." + getterName + "()");
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
    final PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(className, scope);
    final PsiClass fromClass = JavaPsiFacade.getInstance(myProject).findClass(fromName, scope);
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
                                    final Object2IntMap<String> class2variableIndex,
                                    final PsiClass aClass) {
    if (component2variable.containsKey(component)) {
      return component2variable.get(component);
    }

    if (component.getBinding() != null) {
      return component.getBinding();
    }

    final @NonNls String className = component instanceof LwNestedForm ? "nestedForm" : component.getComponentClassName();

    String result = generateUniqueVariableName(className, class2variableIndex, aClass);
    component2variable.put(component, result);

    return result;
  }

  private static String generateUniqueVariableName(final @NonNls String className,
                                                   final Object2IntMap<String> class2variableIndex,
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

    if (!class2variableIndex.containsKey(className)) {
      class2variableIndex.put(className, 0);
    }
    String result;
    do {
      class2variableIndex.mergeInt(className, 1, Math::addExact);
      int newIndex = class2variableIndex.getInt(className);

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


  void startMethodCall(final @NonNls String variable, final @NonNls String methodName) {
    checkParameter();

    append(variable);
    myBuffer.append('.');
    myBuffer.append(methodName);
    myBuffer.append('(');

    myIsFirstParameterStack.push(true);
  }

  private void startStaticMethodCall(final String fullClassName, final @NonNls String methodName) {
    checkParameter();

    myBuffer.append(fullClassName);
    myBuffer.append('.');
    myBuffer.append(methodName);
    myBuffer.append('(');

    myIsFirstParameterStack.push(true);
  }

  private void startStaticMethodCall(final Class<?> aClass, final @NonNls String methodName) {
    startStaticMethodCall(aClass.getCanonicalName(), methodName);
  }

  void endMethod() {
    myBuffer.append(')');

    myIsFirstParameterStack.pop();

    if (myIsFirstParameterStack.isEmpty()) {
      myBuffer.append(";\n");
    }
  }

  void startConstructor(final String className) {
    checkParameter();

    myBuffer.append("new ");
    myBuffer.append(className);
    myBuffer.append('(');

    myIsFirstParameterStack.push(true);
  }

  void endConstructor() {
    endMethod();
  }

  void push(final byte value) {
    checkParameter();
    append(value);
  }

  void append(byte value) {
    myBuffer.append("(byte) ");
    myBuffer.append(value);
  }

  void push(final short value) {
    checkParameter();
    append(value);
  }

  void append(short value) {
    myBuffer.append("(short) ");
    myBuffer.append(value);
  }

  void push(final int value) {
    checkParameter();
    append(value);
  }

  void append(final int value) {
    myBuffer.append(value);
  }

  void push(final int value, final Int2ObjectMap<String> map){
    final String stringRepresentation = map.get(value);
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

  private void push(final long value) {
    checkParameter();
    append(value);
  }

  public void append(final long value) {
    myBuffer.append(value).append("L");
  }

  private void push(final char value) {
    checkParameter();
    append(value);
  }

  public void append(final char value) {
    myBuffer.append("'").append(value).append("'");
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

  void pushVar(final @NonNls String variable) {
    checkParameter();
    append(variable);
  }

  void append(final @NonNls String text) {
    myBuffer.append(text);
  }

  void checkParameter() {
    if (!myIsFirstParameterStack.isEmpty()) {
      final boolean b = myIsFirstParameterStack.pop();
      if (!b) {
        myBuffer.append(',');
      }
      myIsFirstParameterStack.push(false);
    }
  }
}
