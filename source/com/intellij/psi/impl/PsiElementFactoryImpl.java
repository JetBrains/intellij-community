package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.light.*;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.xml.util.XmlTagTextUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;

public class PsiElementFactoryImpl extends PsiJavaParserFacadeImpl implements PsiElementFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiElementFactoryImpl");

  private PsiClass ARRAY_CLASS;
  private PsiClass ARRAY_CLASS15;

  static {
    initPrimitiveTypes();
  }

  private static void initPrimitiveTypes() {
    ourPrimitiveTypesMap.put(PsiType.BYTE.getCanonicalText(), (PsiPrimitiveType)PsiType.BYTE);
    ourPrimitiveTypesMap.put(PsiType.CHAR.getCanonicalText(), (PsiPrimitiveType)PsiType.CHAR);
    ourPrimitiveTypesMap.put(PsiType.DOUBLE.getCanonicalText(), (PsiPrimitiveType)PsiType.DOUBLE);
    ourPrimitiveTypesMap.put(PsiType.FLOAT.getCanonicalText(), (PsiPrimitiveType)PsiType.FLOAT);
    ourPrimitiveTypesMap.put(PsiType.INT.getCanonicalText(), (PsiPrimitiveType)PsiType.INT);
    ourPrimitiveTypesMap.put(PsiType.LONG.getCanonicalText(), (PsiPrimitiveType)PsiType.LONG);
    ourPrimitiveTypesMap.put(PsiType.SHORT.getCanonicalText(), (PsiPrimitiveType)PsiType.SHORT);
    ourPrimitiveTypesMap.put(PsiType.BOOLEAN.getCanonicalText(), (PsiPrimitiveType)PsiType.BOOLEAN);
    ourPrimitiveTypesMap.put(PsiType.VOID.getCanonicalText(), (PsiPrimitiveType)PsiType.VOID);
    ourPrimitiveTypesMap.put(PsiType.NULL.getCanonicalText(), (PsiPrimitiveType)PsiType.NULL);
  }

  public PsiElementFactoryImpl(PsiManagerEx manager) {
    super(manager);
  }

  public PsiJavaFile getDummyJavaFile() {
    if (myDummyJavaFile == null) {
      myDummyJavaFile = createDummyJavaFile("");
    }

    return myDummyJavaFile;
  }

  @NotNull
  public PsiClass getArrayClass(@NotNull LanguageLevel languageLevel) {
    try {
      if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
        if (ARRAY_CLASS == null) {
          ARRAY_CLASS = createClassFromText("public class __Array__{\n public final int length; \n public Object clone(){}\n}", null).getInnerClasses()[0];
        }
        return ARRAY_CLASS;
      }
      else {
        if (ARRAY_CLASS15 == null) {
          ARRAY_CLASS15 = createClassFromText("public class __Array__<T>{\n public final int length; \n public T[] clone(){}\n}", null).getInnerClasses()[0];
        }
        return ARRAY_CLASS15;
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  @NotNull
  public PsiClassType getArrayClassType(@NotNull PsiType componentType, @NotNull final LanguageLevel languageLevel) {
    PsiClass arrayClass = getArrayClass(languageLevel);
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    PsiTypeParameter[] typeParameters = arrayClass.getTypeParameters();
    if (typeParameters.length == 1) {
      substitutor = substitutor.put(typeParameters[0], componentType);
    }

    return createType(arrayClass, substitutor);
  }

  @NotNull
  public PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor) {
    return new PsiImmediateClassType(resolve, substitutor);
  }

  @NotNull
  public PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor, @NotNull LanguageLevel languageLevel) {
    return new PsiImmediateClassType(resolve, substitutor, languageLevel);
  }

  @NotNull
  public PsiClass createClass(@NotNull String name) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, name);
    @NonNls String text = "public class " + name + "{ }";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException();
    }
    return classes[0];
  }

  @NotNull
  public PsiClass createInterface(@NotNull String name) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, name);
    @NonNls String text = "public interface " + name + "{ }";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException();
    }
    return classes[0];
  }

  public PsiClass createEnum(@NotNull final String name) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, name);
    @NonNls String text = "public enum " + name + "{ }";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException();
    }
    return classes[0];
  }

  @NotNull
  public PsiTypeElement createTypeElement(@NotNull PsiType psiType) {
    final LightTypeElement element = new LightTypeElement(myManager, psiType);
    CodeEditUtil.setNodeGenerated(element.getNode(), true);
    return element;
  }

  @NotNull
  public PsiJavaCodeReferenceElement createReferenceElementByType(@NotNull PsiClassType type) {
    if (type instanceof PsiClassReferenceType) {
      return ((PsiClassReferenceType)type).getReference();
    }

    final PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
    return new LightClassReference(myManager, type.getPresentableText(), resolveResult.getElement(), resolveResult.getSubstitutor());
  }

  @NotNull
  public PsiField createField(@NotNull String name, @NotNull PsiType type) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, name);
    if (type == PsiType.NULL) {
      throw new IncorrectOperationException("Cannot create field with type \"<null_type>\".");
    }
    TreeElement typeCopy = ChangeUtil.copyToElement(createTypeElement(type));
    typeCopy.acceptTree(new GeneratedMarkerVisitor());
    @NonNls String text = "class _Dummy_ {private int " + name + ";}";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiClass aClass = aFile.getClasses()[0];
    PsiField field = aClass.getFields()[0];
    SourceTreeToPsiMap.psiElementToTree(field).replaceChild(SourceTreeToPsiMap.psiElementToTree(field.getTypeElement()), typeCopy);
    ChangeUtil.decodeInformation((TreeElement)SourceTreeToPsiMap.psiElementToTree(field));
    return (PsiField)CodeStyleManager.getInstance(myManager.getProject()).reformat(field);
  }

  @NotNull
  public PsiMethod createMethod(@NotNull String name, PsiType returnType) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, name);
    if (returnType == PsiType.NULL) {
      throw new IncorrectOperationException("Cannot create field with type \"<null_type>\".");
    }
    @NonNls String text = "class _Dummy_ {\n public void " + name + "(){}\n}";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiClass aClass = aFile.getClasses()[0];
    PsiMethod method = aClass.getMethods()[0];
    method.getReturnTypeElement().replace(createTypeElement(returnType));
    return (PsiMethod)CodeStyleManager.getInstance(myManager.getProject()).reformat(method);
  }

  @NotNull
  public PsiMethod createConstructor() {
    try {
      @NonNls String text = "class _Dummy_ {\n public _Dummy_(){}\n}";
      PsiJavaFile aFile = createDummyJavaFile(text);
      PsiClass aClass = aFile.getClasses()[0];
      PsiMethod method = aClass.getMethods()[0];
      return (PsiMethod)CodeStyleManager.getInstance(myManager.getProject()).reformat(method);
    }
    catch (IncorrectOperationException e) {
      LOG.assertTrue(false);
      return null;
    }
  }

  @NotNull
  public PsiClassInitializer createClassInitializer() throws IncorrectOperationException {
    @NonNls String text = "class _Dummy_ { {} }";
    final PsiJavaFile aFile = createDummyJavaFile(text);
    final PsiClass aClass = aFile.getClasses()[0];
    final PsiClassInitializer psiClassInitializer = aClass.getInitializers()[0];
    return (PsiClassInitializer)CodeStyleManager.getInstance(myManager.getProject()).reformat(psiClassInitializer);
  }

  @NotNull
  public PsiParameter createParameter(@NotNull String name, @NotNull PsiType type) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, name);
    if (type == PsiType.NULL) {
      throw new IncorrectOperationException("Cannot create field with type \"<null_type>\".");
    }
    final FileElement treeHolder = new DummyHolder(myManager, null).getTreeElement();
    final CompositeElement treeElement =
    getJavaParsingContext(treeHolder).getDeclarationParsing().parseParameterText(PsiKeyword.INT + " " + name);
    TreeUtil.addChildren(treeHolder, treeElement);

    TreeElement typeElement = ChangeUtil.copyToElement(createTypeElement(type));
    treeElement.replaceChild(treeElement.findChildByRole(ChildRole.TYPE), typeElement);
    ChangeUtil.decodeInformation(typeElement);

    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myManager.getProject());
    PsiParameter parameter = (PsiParameter)SourceTreeToPsiMap.treeElementToPsi(treeElement);
    parameter.getModifierList()
      .setModifierProperty(PsiModifier.FINAL, CodeStyleSettingsManager.getSettings(myManager.getProject()).GENERATE_FINAL_PARAMETERS);
    treeElement.acceptTree(new GeneratedMarkerVisitor());
    return (PsiParameter)codeStyleManager.reformat(parameter);
  }

  @NotNull
  public PsiCodeBlock createCodeBlock() {
    try {
      PsiCodeBlock block = createCodeBlockFromText("{}", null);
      return (PsiCodeBlock)CodeStyleManager.getInstance(myManager.getProject()).reformat(block);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  @NotNull
  public PsiClassType createType(@NotNull PsiClass aClass) {
    return new PsiImmediateClassType(aClass, aClass instanceof PsiTypeParameter ? PsiSubstitutor.EMPTY : createRawSubstitutor(aClass));
  }

  @NotNull
  public PsiClassType createType(@NotNull PsiJavaCodeReferenceElement classReference) {
    return new PsiClassReferenceType(classReference);
  }

  private static class TypeDetacher extends PsiTypeVisitor<PsiType> {
    public static final TypeDetacher INSTANCE = new TypeDetacher();

    public PsiType visitType(PsiType type) {
      return type;
    }

    public PsiType visitWildcardType(PsiWildcardType wildcardType) {
      final PsiType bound = wildcardType.getBound();
      if (bound == null) {
        return wildcardType;
      }
      else {
        return PsiWildcardType.changeBound(wildcardType, bound.accept(this));
      }
    }

    public PsiType visitArrayType(PsiArrayType arrayType) {
      final PsiType componentType = arrayType.getComponentType();
      final PsiType detachedComponentType = componentType.accept(this);
      if (detachedComponentType == componentType) return arrayType; // optimization
      return detachedComponentType.createArrayType();
    }

    public PsiType visitClassType(PsiClassType classType) {
      final PsiClassType.ClassResolveResult resolveResult = classType.resolveGenerics();
      final PsiClass aClass = resolveResult.getElement();
      if (aClass == null) return classType;
      final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(aClass);
      final HashMap<PsiTypeParameter, PsiType> map = new HashMap<PsiTypeParameter, PsiType>();
      while (iterator.hasNext()) {
        PsiTypeParameter parameter = iterator.next();
        PsiType type = resolveResult.getSubstitutor().substitute(parameter);
        if (type != null) {
          type = type.accept(this);
        }
        map.put(parameter, type);
      }
      return new PsiImmediateClassType(aClass, PsiSubstitutorImpl.createSubstitutor(map));
    }
  }


  @NotNull
  public PsiType detachType(@NotNull PsiType type) {
    return type.accept(TypeDetacher.INSTANCE);
  }

  @NotNull
  public PsiSubstitutor createRawSubstitutor(@NotNull PsiTypeParameterListOwner owner) {
    final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(owner);
    if (!iterator.hasNext()) return PsiSubstitutor.EMPTY;
    Map<PsiTypeParameter, PsiType> substMap = new HashMap<PsiTypeParameter, PsiType>();
    while (iterator.hasNext()) {
      substMap.put(iterator.next(), null);
    }
    return PsiSubstitutorImpl.createSubstitutor(substMap);
  }

  @NotNull
  public PsiSubstitutor createSubstitutor(@NotNull Map<PsiTypeParameter, PsiType> map) {
    return PsiSubstitutorImpl.createSubstitutor(map);
  }

  @Nullable
  public PsiPrimitiveType createPrimitiveType(@NotNull String text) {
    return getPrimitiveType(text);
  }

  public static PsiPrimitiveType getPrimitiveType(final String text) {
    return ourPrimitiveTypesMap.get(text);
  }

  @NotNull
  public PsiClassType createTypeByFQClassName(@NotNull String qName) {
    return createTypeByFQClassName(qName, GlobalSearchScope.allScope(myManager.getProject()));
  }

  @NotNull
  public PsiClassType createTypeByFQClassName(@NotNull String qName, @NotNull GlobalSearchScope resolveScope) {
    return new PsiClassReferenceType(createReferenceElementByFQClassName(qName, resolveScope));
  }

  @NotNull
  public PsiJavaCodeReferenceElement createClassReferenceElement(@NotNull PsiClass aClass) {
    final String text;
    if (aClass instanceof PsiAnonymousClass) {
      text = ((PsiAnonymousClass)aClass).getBaseClassType().getPresentableText();
    }
    else {
      text = aClass.getName();
    }
    return new LightClassReference(myManager, text, aClass);
  }

  @NotNull
  public PsiJavaCodeReferenceElement createReferenceElementByFQClassName(@NotNull String qName, @NotNull GlobalSearchScope resolveScope) {
    String shortName = PsiNameHelper.getShortClassName(qName);
    return new LightClassReference(myManager, shortName, qName, resolveScope);
  }

  @NotNull
  public PsiJavaCodeReferenceElement createFQClassNameReferenceElement(@NotNull String qName, @NotNull GlobalSearchScope resolveScope) {
    return new LightClassReference(myManager, qName, qName, resolveScope);
  }

  @NotNull
  public PsiJavaCodeReferenceElement createPackageReferenceElement(@NotNull PsiPackage aPackage)
    throws IncorrectOperationException {
    if (aPackage.getQualifiedName().length() == 0) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReference(myManager, aPackage);
  }

  @NotNull
  public PsiPackageStatement createPackageStatement(@NotNull String name) throws IncorrectOperationException {
    final PsiJavaFile javaFile = (PsiJavaFile)createFileFromText("dummy.java", "package " + name + ";");
    final PsiPackageStatement stmt = javaFile.getPackageStatement();
    if (stmt == null) throw new IncorrectOperationException("Incorrect package name: " + name);
    return stmt;
  }

  @NotNull
  public XmlTag getAntImplicitDeclarationTag() throws IncorrectOperationException {
    return createTagFromText(
        "<implicit-properties-ant-declaration-tag-for-intellij-idea xmlns=\"" + XmlUtil.ANT_URI + "\"/>");
  }

  @NotNull
  public PsiJavaCodeReferenceCodeFragment createReferenceCodeFragment(@NotNull String text,
                                                                      PsiElement context,
                                                                      boolean isPhysical,
                                                                      boolean isClassesAccepted) {
    final PsiJavaCodeReferenceCodeFragmentImpl result =
      new PsiJavaCodeReferenceCodeFragmentImpl(myManager.getProject(), isPhysical, "fragment.java", text, isClassesAccepted);
    result.setContext(context);
    return result;
  }

  @NotNull
  public PsiImportStaticStatement createImportStaticStatement(@NotNull PsiClass aClass, @NotNull String memberName) throws IncorrectOperationException {
    if (aClass instanceof PsiAnonymousClass) {
      throw new IncorrectOperationException("Cannot create import statement for anonymous class.");
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) {
      throw new IncorrectOperationException("Cannot create import statement for local class.");
    }
    @NonNls String text = "import static " + aClass.getQualifiedName() + "." + memberName + ";";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiImportStaticStatement statement = aFile.getImportList().getImportStaticStatements()[0];
    return (PsiImportStaticStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @NotNull
  public PsiParameterList createParameterList(@NotNull String[] names, @NotNull PsiType[] types) throws IncorrectOperationException {
    @NonNls String text = "void method(";
    String sep = "";
    for (int i = 0; i < names.length; i++) {
      final String name = names[i];
      PsiType type = types[i];
      text += sep + type.getCanonicalText() + " " + name;
      sep = ",";
    }
    text += "){}";
    PsiMethod method = createMethodFromText(text, null);
    return method.getParameterList();
  }

  @NotNull
  public PsiReferenceList createReferenceList(@NotNull PsiJavaCodeReferenceElement[] references) throws IncorrectOperationException {
    @NonNls String text = "void method() ";
    if (references.length > 0) text += "throws ";
    String sep = "";
    for (final PsiJavaCodeReferenceElement reference : references) {
      text += sep + reference.getCanonicalText();
      sep = ",";
    }
    text += "{}";
    PsiMethod method = createMethodFromText(text, null);
    return method.getThrowsList();
  }

  @NotNull
  public XmlText createDisplayText(@NotNull String s) throws IncorrectOperationException {
    final XmlTag tagFromText = createTagFromText("<a>" + XmlTagTextUtil.getCDATAQuote(s) + "</a>");
    final XmlText[] textElements = tagFromText.getValue().getTextElements();
    if (textElements.length == 0) return (XmlText)Factory.createCompositeElement(XmlElementType.XML_TEXT);
    return textElements[0];
  }

  @NotNull
  public XmlTag createXHTMLTagFromText(@NotNull String text) throws IncorrectOperationException {
    return ((XmlFile)createFileFromText("dummy.xhtml", text)).getDocument().getRootTag();
  }

  @NotNull
  public PsiJavaCodeReferenceElement createPackageReferenceElement(@NotNull String packageName)
    throws IncorrectOperationException {
    if (packageName.length() == 0) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReference(myManager, packageName);
  }

  @NotNull
  public PsiReferenceExpression createReferenceExpression(@NotNull PsiClass aClass) throws IncorrectOperationException {
    String text;
    if (aClass instanceof PsiAnonymousClass) {
      text = ((PsiAnonymousClass)aClass).getBaseClassType().getPresentableText();
    }
    else {
      text = aClass.getName();
    }
    return new LightClassReferenceExpression(myManager, text, aClass);
  }

  @NotNull
  public PsiReferenceExpression createReferenceExpression(@NotNull PsiPackage aPackage) throws IncorrectOperationException {
    if (aPackage.getQualifiedName().length() == 0) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReferenceExpression(myManager, aPackage);
  }

  @NotNull
  public PsiIdentifier createIdentifier(@NotNull String text) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, text);
    return new LightIdentifier(myManager, text);
  }

  @NotNull
  public PsiKeyword createKeyword(@NotNull String text) throws IncorrectOperationException {
    if (!myManager.getNameHelper().isKeyword(text)) {
      throw new IncorrectOperationException("\"" + text + "\" is not a keyword.");
    }
    return new LightKeyword(myManager, text);
  }

  @NotNull
  public PsiImportStatement createImportStatement(@NotNull PsiClass aClass) throws IncorrectOperationException {
    if (aClass instanceof PsiAnonymousClass) {
      throw new IncorrectOperationException("Cannot create import statement for anonymous class.");
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) {
      throw new IncorrectOperationException("Cannot create import statement for local class.");
    }
    @NonNls String text = "import " + aClass.getQualifiedName() + ";";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiImportStatement statement = aFile.getImportList().getImportStatements()[0];
    return (PsiImportStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @NotNull
  public PsiImportStatement createImportStatementOnDemand(@NotNull String packageName) throws IncorrectOperationException {
    if (packageName.length() == 0) {
      throw new IncorrectOperationException("Cannot create import statement for default package.");
    }
    if (!myManager.getNameHelper().isQualifiedName(packageName)) {
      throw new IncorrectOperationException("Incorrect package name: \"" + packageName + "\".");
    }

    @NonNls String text = "import " + packageName + ".*;";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiImportStatement statement = aFile.getImportList().getImportStatements()[0];
    return (PsiImportStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  @NotNull
  public PsiDeclarationStatement createVariableDeclarationStatement(@NotNull String name, @NotNull PsiType type, PsiExpression initializer)
    throws IncorrectOperationException {
    if (!myManager.getNameHelper().isIdentifier(name)) {
      throw new IncorrectOperationException("\"" + name + "\" is not an identifier.");
    }
    if (type == PsiType.NULL) {
      throw new IncorrectOperationException("Cannot create field with type \"<null_type>\".");
    }
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append("X ");
    buffer.append(name);
    if (initializer != null) {
      buffer.append("=x");
    }
    buffer.append(";");
    PsiDeclarationStatement statement = (PsiDeclarationStatement)createStatementFromText(buffer.toString(), null);
    PsiVariable variable = (PsiVariable)statement.getDeclaredElements()[0];
    variable.getTypeElement().replace(createTypeElement(type));
    variable.getModifierList()
      .setModifierProperty(PsiModifier.FINAL, CodeStyleSettingsManager.getSettings(myManager.getProject()).GENERATE_FINAL_LOCALS);
    if (initializer != null) {
      variable.getInitializer().replace(initializer);
    }
    markGenerated(statement);
    return statement;

  }

  @NotNull
  public PsiDocTag createParamTag(@NotNull String parameterName, @NonNls String description) throws IncorrectOperationException {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append(" * @param ");
    buffer.append(parameterName);
    buffer.append(" ");
    final String[] strings = description.split("\\n");
    for (int i = 0; i < strings.length; i++) {
      String string = strings[i];
      if (i > 0) buffer.append("\n * ");
      buffer.append(string);
    }
    return createDocTagFromText(buffer.toString(), null);
  }

  @NotNull
  public XmlTag createTagFromText(@NotNull String text) throws IncorrectOperationException {
    final XmlTag tag = ((XmlFile)createFileFromText("dummy.xml", text)).getDocument().getRootTag();
    if (tag == null) throw new IncorrectOperationException("Incorrect tag text");
    return tag;
  }

  @NotNull
  public XmlAttribute createXmlAttribute(@NotNull String name, String value) throws IncorrectOperationException {
    XmlTag tag = ((XmlFile)createFileFromText("dummy.xml", "<tag " + name + "=\"" + value + "\"/>")).getDocument().getRootTag();
    return tag.getAttributes()[0];
  }

  @NotNull
  public PsiExpressionCodeFragment createExpressionCodeFragment(@NotNull String text,
                                                                PsiElement context,
                                                                final PsiType expectedType,
                                                                boolean isPhysical) {
    final PsiExpressionCodeFragmentImpl result = new PsiExpressionCodeFragmentImpl(
    myManager.getProject(), isPhysical, "fragment.java", text, expectedType);
    result.setContext(context);
    return result;
  }

  @NotNull
  public PsiCodeFragment createCodeBlockCodeFragment(@NotNull String text, PsiElement context, boolean isPhysical) {
    final PsiCodeFragmentImpl result = new PsiCodeFragmentImpl(myManager.getProject(), JavaElementType.STATEMENTS,
                                                               isPhysical,
                                                               "fragment.java",
                                                               text);
    result.setContext(context);
    return result;
  }

  @NotNull
  public PsiTypeCodeFragment createTypeCodeFragment(@NotNull String text, PsiElement context, boolean isPhysical) {

    return createTypeCodeFragment(text, context, false, isPhysical, false);
  }


  @NotNull
  public PsiTypeCodeFragment createTypeCodeFragment(@NotNull String text,
                                                    PsiElement context,
                                                    boolean isVoidValid,
                                                    boolean isPhysical) {
    return createTypeCodeFragment(text, context, true, isPhysical, false);
  }

  @NotNull
  public PsiTypeCodeFragment createTypeCodeFragment(@NotNull String text,
                                                    PsiElement context,
                                                    boolean isVoidValid,
                                                    boolean isPhysical,
                                                    boolean allowEllipsis) {
    final PsiTypeCodeFragmentImpl result = new PsiTypeCodeFragmentImpl(myManager.getProject(),
                                                                       isPhysical,
                                                                       allowEllipsis,
                                                                       "fragment.java",
                                                                       text);
    result.setContext(context);
    if (isVoidValid) {
      result.putUserData(PsiUtil.VALID_VOID_TYPE_IN_CODE_FRAGMENT, Boolean.TRUE);
    }
    return result;
  }


  @NotNull
  public PsiAnnotation createAnnotationFromText(@NotNull final String annotationText, final PsiElement context) throws IncorrectOperationException {
    final PsiAnnotation psiAnnotation = super.createAnnotationFromText(annotationText, context);
    markGenerated(psiAnnotation);
    return psiAnnotation;
  }

  @NotNull
  public PsiCodeBlock createCodeBlockFromText(@NotNull final String text, final PsiElement context) throws IncorrectOperationException {
    final PsiCodeBlock psiCodeBlock = super.createCodeBlockFromText(text, context);
    markGenerated(psiCodeBlock);
    return psiCodeBlock;
  }

  @NotNull
  public PsiEnumConstant createEnumConstantFromText(@NotNull final String text, final PsiElement context) throws IncorrectOperationException {
    final PsiEnumConstant enumConstant = super.createEnumConstantFromText(text, context);
    markGenerated(enumConstant);
    return enumConstant;
  }

  @NotNull
  public PsiExpression createExpressionFromText(@NotNull final String text, final PsiElement context) throws IncorrectOperationException {
    final PsiExpression expression = super.createExpressionFromText(text, context);
    markGenerated(expression);
    return expression;
  }

  @NotNull
  public PsiField createFieldFromText(@NotNull final String text, final PsiElement context) throws IncorrectOperationException {
    final PsiField psiField = super.createFieldFromText(text, context);
    markGenerated(psiField);
    return psiField;
  }

  @NotNull
  public PsiParameter createParameterFromText(@NotNull final String text, final PsiElement context) throws IncorrectOperationException {
    final PsiParameter parameter = super.createParameterFromText(text, context);
    markGenerated(parameter);
    return parameter;
  }

  @NotNull
  public PsiStatement createStatementFromText(@NotNull final String text, final PsiElement context) throws IncorrectOperationException {
    final PsiStatement statement = super.createStatementFromText(text, context);
    markGenerated(statement);
    return statement;
  }

  @NotNull
  public PsiType createTypeFromText(@NotNull final String text, final PsiElement context) throws IncorrectOperationException {
    return createTypeInner(text, context, true);
  }


  @NotNull
  public PsiTypeParameter createTypeParameterFromText(@NotNull final String text, final PsiElement context) throws
                                                                                                            IncorrectOperationException {
    final PsiTypeParameter typeParameter = super.createTypeParameterFromText(text, context);
    markGenerated(typeParameter);
    return typeParameter;
  }

  @NotNull
  public PsiElement createWhiteSpaceFromText(@NotNull @NonNls final String text) throws IncorrectOperationException {
    final PsiElement whitespace = super.createWhiteSpaceFromText(text);
    markGenerated(whitespace);
    return whitespace;
  }

  @NotNull
  public PsiMethod createMethodFromText(@NotNull final String text, final PsiElement context, final LanguageLevel level) throws
                                                                                                                         IncorrectOperationException {
    final PsiMethod method = super.createMethodFromText(text, context, level);
    markGenerated(method);
    return method;
  }


  @NotNull
  public PsiCatchSection createCatchSection(@NotNull final PsiClassType exceptionType,
                                            @NotNull final String exceptionName, final PsiElement context) throws
                                                                                                           IncorrectOperationException {
    final PsiCatchSection psiCatchSection = super.createCatchSection(exceptionType, exceptionName, context);
    markGenerated(psiCatchSection);
    return psiCatchSection;
  }

  private static void markGenerated(final PsiElement element) {
    ((TreeElement)element.getNode()).acceptTree(new GeneratedMarkerVisitor());
  }
}
