package com.intellij.psi.impl;

import com.intellij.aspects.psi.PsiAspectFile;
import com.intellij.aspects.psi.PsiTypePattern;
import com.intellij.aspects.psi.PsiWithinPointcut;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.light.*;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.parsing.DeclarationParsing;
import com.intellij.psi.impl.source.parsing.ExpressionParsing;
import com.intellij.psi.impl.source.parsing.JavaParsingContext;
import com.intellij.psi.impl.source.parsing.Parsing;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.xml.util.XmlTagTextUtil;
import com.intellij.xml.util.XmlUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public class PsiElementFactoryImpl implements PsiElementFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiElementFactoryImpl");

  private PsiClass ARRAY_CLASS;

  private final PsiManagerImpl myManager;
  private static final Map<String, PsiPrimitiveType> ourPrimitiveTypesMap = new HashMap<String, PsiPrimitiveType>();

  static {
    ourPrimitiveTypesMap.put("byte", (PsiPrimitiveType)PsiType.BYTE);
    ourPrimitiveTypesMap.put("char", (PsiPrimitiveType)PsiType.CHAR);
    ourPrimitiveTypesMap.put("double", (PsiPrimitiveType)PsiType.DOUBLE);
    ourPrimitiveTypesMap.put("float", (PsiPrimitiveType)PsiType.FLOAT);
    ourPrimitiveTypesMap.put("int", (PsiPrimitiveType)PsiType.INT);
    ourPrimitiveTypesMap.put("long", (PsiPrimitiveType)PsiType.LONG);
    ourPrimitiveTypesMap.put("short", (PsiPrimitiveType)PsiType.SHORT);
    ourPrimitiveTypesMap.put("boolean", (PsiPrimitiveType)PsiType.BOOLEAN);
    ourPrimitiveTypesMap.put("void", (PsiPrimitiveType)PsiType.VOID);
    ourPrimitiveTypesMap.put("null", (PsiPrimitiveType)PsiType.NULL);
  }

  private PsiJavaFile myDummyJavaFile;

  public PsiElementFactoryImpl(PsiManagerImpl manager) {
    myManager = manager;
  }

  public PsiJavaFile getDummyJavaFile() {
    if (myDummyJavaFile == null) {
      try {
        myDummyJavaFile = createDummyJavaFile("");
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    return myDummyJavaFile;
  }

  public PsiClass getArrayClass() {
    if (ARRAY_CLASS == null) {
      try {
        if (myManager.getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_5) < 0) {
          ARRAY_CLASS = createClassFromText(
            "public class __Array__{\n public final int length; \n public Object clone(){}\n}", null);
        }
        else {
          ARRAY_CLASS = createClassFromText(
            "public class __Array__<T>{\n public final int length; \n public T[] clone(){}\n}", null);
        }
        CodeStyleManager.getInstance(myManager.getProject()).reformat(ARRAY_CLASS);
        ARRAY_CLASS = ARRAY_CLASS.getInnerClasses()[0];
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    return ARRAY_CLASS;
  }

  public PsiClassType getArrayClassType(PsiType componentType) {
    PsiClass arrayClass = getArrayClass();
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    PsiTypeParameter[] typeParameters = arrayClass.getTypeParameters();
    if (typeParameters.length == 1) {
      substitutor = substitutor.put(typeParameters[0], componentType);
    }

    return createType(arrayClass, substitutor);
  }

  public PsiClassType createType(PsiClass resolve, PsiSubstitutor substitutor) {
    return new PsiImmediateClassType(resolve, substitutor);
  }

  public PsiClass createClass(String name) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, name);
    String text = "public class " + name + "{ }";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException();
    }
    return classes[0];
  }

  public PsiClass createInterface(String name) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, name);
    String text = "public interface " + name + "{ }";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException();
    }
    return classes[0];
  }

  public PsiTypeElement createTypeElement(PsiType psiType) {
    return new LightTypeElement(myManager, psiType);
  }

  public PsiJavaCodeReferenceElement createReferenceElementByType(PsiClassType type) {
    if (type instanceof PsiClassReferenceType) {
      return ((PsiClassReferenceType)type).getReference();
    }

    final PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
    return new LightClassReference(myManager, type.getPresentableText(), resolveResult.getElement(), resolveResult.getSubstitutor());
  }

  public PsiField createField(String name, PsiType type) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, name);
    if (type == PsiType.NULL) {
      throw new IncorrectOperationException("Cannot create field with type \"<null_type>\".");
    }
    ASTNode typeCopy = ChangeUtil.copyToElement(createTypeElement(type));
    String text = "class _Dummy_ {private int " + name + ";}";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiClass aClass = aFile.getClasses()[0];
    PsiField field = aClass.getFields()[0];
    SourceTreeToPsiMap.psiElementToTree(field).replaceChild(SourceTreeToPsiMap.psiElementToTree(field.getTypeElement()), typeCopy);
    ChangeUtil.decodeInformation((TreeElement)SourceTreeToPsiMap.psiElementToTree(field));
    return (PsiField)CodeStyleManager.getInstance(myManager.getProject()).reformat(field);
  }

  public PsiMethod createMethod(String name, PsiType returnType) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, name);
    if (returnType == PsiType.NULL) {
      throw new IncorrectOperationException("Cannot create field with type \"<null_type>\".");
    }
    String text = "class _Dummy_ {\n public void " + name + "(){}\n}";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiClass aClass = aFile.getClasses()[0];
    PsiMethod method = aClass.getMethods()[0];
    method.getReturnTypeElement().replace(createTypeElement(returnType));
    return (PsiMethod)CodeStyleManager.getInstance(myManager.getProject()).reformat(method);
  }

  public PsiMethod createConstructor() {
    String text = "class _Dummy_ {\n public _Dummy_(){}\n}";
    try {
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

  public PsiClassInitializer createClassInitializer() throws IncorrectOperationException {
    String text = "class _Dummy_ { {} }";
    final PsiJavaFile aFile = createDummyJavaFile(text);
    final PsiClass aClass = aFile.getClasses()[0];
    final PsiClassInitializer psiClassInitializer = aClass.getInitializers()[0];
    return (PsiClassInitializer)CodeStyleManager.getInstance(myManager.getProject()).reformat(psiClassInitializer);
  }

  public PsiParameter createParameter(String name, PsiType type) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, name);
    if (type == PsiType.NULL) {
      throw new IncorrectOperationException("Cannot create field with type \"<null_type>\".");
    }
    final FileElement treeHolder = new DummyHolder(myManager, null).getTreeElement();
    final CompositeElement treeElement =
    getJavaParsingContext(treeHolder).getDeclarationParsing().parseParameterText(("int " + name).toCharArray());
    TreeUtil.addChildren(treeHolder, treeElement);

    TreeElement typeElement = ChangeUtil.copyToElement(createTypeElement(type));
    treeElement.replaceChild(treeElement.findChildByRole(ChildRole.TYPE), typeElement);
    ChangeUtil.decodeInformation(typeElement);

    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myManager.getProject());
    PsiParameter parameter = (PsiParameter)SourceTreeToPsiMap.treeElementToPsi(treeElement);
    parameter.getModifierList()
      .setModifierProperty(PsiModifier.FINAL, CodeStyleSettingsManager.getSettings(myManager.getProject()).GENERATE_FINAL_PARAMETERS);
    return (PsiParameter)codeStyleManager.reformat(parameter);
  }

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

  public PsiClassType createType(PsiClass aClass) {
    return new PsiImmediateClassType(aClass, aClass instanceof PsiTypeParameter ? PsiSubstitutor.EMPTY : createRawSubstitutor(aClass));
  }

  public PsiClassType createType(PsiJavaCodeReferenceElement classReference) {
    LOG.assertTrue(classReference != null);
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


  public PsiType detachType(PsiType type) {
    return type.accept(TypeDetacher.INSTANCE);
  }

  public PsiSubstitutor createRawSubstitutor(PsiClass aClass) {
    final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(aClass);
    if (!iterator.hasNext()) return PsiSubstitutor.EMPTY;
    Map<PsiTypeParameter, PsiType> substMap = new HashMap<PsiTypeParameter, PsiType>();
    while (iterator.hasNext()) {
      substMap.put(iterator.next(), null);
    }
    return PsiSubstitutorImpl.createSubstitutor(substMap);
  }

  public PsiSubstitutor createSubstitutor(Map<PsiTypeParameter, PsiType> map) {
    return PsiSubstitutorImpl.createSubstitutor(map);
  }

  public PsiPrimitiveType createPrimitiveType(String text) {
    return ourPrimitiveTypesMap.get(text);
  }

  public PsiClassType createTypeByFQClassName(String qName) {
    return createTypeByFQClassName(qName, GlobalSearchScope.allScope(myManager.getProject()));
  }

  public PsiClassType createTypeByFQClassName(String qName, GlobalSearchScope resolveScope) {
    return new PsiClassReferenceType(createReferenceElementByFQClassName(qName, resolveScope));
  }

  public PsiJavaCodeReferenceElement createClassReferenceElement(PsiClass aClass) {
    final String text;
    if (aClass instanceof PsiAnonymousClass) {
      text = ((PsiAnonymousClass)aClass).getBaseClassType().getPresentableText();
    }
    else {
      text = aClass.getName();
    }
    return new LightClassReference(myManager, text, aClass);
  }

  public PsiJavaCodeReferenceElement createReferenceElementByFQClassName(String qName, GlobalSearchScope resolveScope) {
    String shortName = PsiNameHelper.getShortClassName(qName);
    return new LightClassReference(myManager, shortName, qName, resolveScope);
  }

  public PsiJavaCodeReferenceElement createFQClassNameReferenceElement(String qName, GlobalSearchScope resolveScope) {
    return new LightClassReference(myManager, qName, qName, resolveScope);
  }

  public PsiJavaCodeReferenceElement createPackageReferenceElement(PsiPackage aPackage)
    throws IncorrectOperationException {
    if (aPackage.getQualifiedName().length() == 0) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReference(myManager, aPackage);
  }

  public PsiPackageStatement createPackageStatement(String name) throws IncorrectOperationException {
    final PsiJavaFile javaFile = (PsiJavaFile)createFileFromText("dummy.java", "package " + name + ";");
    final PsiPackageStatement packageStatement = javaFile.getPackageStatement();
    return packageStatement;
  }

  public XmlTag getAntImplicitDeclarationTag() throws IncorrectOperationException {
    return createTagFromText(
        "<implicit-properties-ant-declaration-tag-for-intellij-idea xmlns=\"" + XmlUtil.ANT_URI + "\"/>");
  }

  public PsiJavaCodeReferenceCodeFragment createReferenceCodeFragment(String text,
                                                                      PsiElement context,
                                                                      boolean isPhysical,
                                                                      boolean isClassesAccepted) {
    final PsiJavaCodeReferenceCodeFragmentImpl result =
      new PsiJavaCodeReferenceCodeFragmentImpl(myManager.getProject(), isPhysical, "fragment.java", text, isClassesAccepted);
    result.setContext(context);
    return result;
  }

  public PsiAnnotation createAnnotationFromText(String annotationText, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    CompositeElement annotationElement =
    getJavaParsingContext(holderElement).getDeclarationParsing().parseAnnotationFromText(myManager, annotationText);
    if (annotationElement == null || annotationElement.getElementType() != ElementType.ANNOTATION) {
      throw new IncorrectOperationException("Incorrect annotation \"" + annotationText + "\".");
    }
    TreeUtil.addChildren(holderElement, annotationElement);
    return (PsiAnnotation)SourceTreeToPsiMap.treeElementToPsi(annotationElement);
  }

  public PsiImportStaticStatement createImportStaticStatement(PsiClass aClass, String memberName) throws IncorrectOperationException {
    if (aClass instanceof PsiAnonymousClass) {
      throw new IncorrectOperationException("Cannot create import statement for anonymous class.");
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) {
      throw new IncorrectOperationException("Cannot create import statement for local class.");
    }
    String text = "import static " + aClass.getQualifiedName() + "." + memberName + ";";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiImportStaticStatement statement = aFile.getImportList().getImportStaticStatements()[0];
    return (PsiImportStaticStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  public PsiParameterList createParameterList(String[] names, PsiType[] types) throws IncorrectOperationException {
    String text = "void method(";
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

  public PsiReferenceList createReferenceList(PsiJavaCodeReferenceElement[] references) throws IncorrectOperationException {
    String text = "void method() ";
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

  public PsiCatchSection createCatchSection(PsiClassType exceptionType,
                                            String exceptionName,
                                            PsiElement context) throws IncorrectOperationException {
    StringBuffer buffer = new StringBuffer();
    buffer.append("catch (");
    buffer.append(exceptionType.getCanonicalText());
    buffer.append(" " + exceptionName + "){}");
    String catchSectionText = buffer.toString();
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    TreeElement catchSection = getJavaParsingContext(holderElement).getStatementParsing().parseCatchSectionText(catchSectionText.toCharArray());
    LOG.assertTrue(catchSection != null && catchSection.getElementType() == ElementType.CATCH_SECTION);
    TreeUtil.addChildren(holderElement, catchSection);
    PsiCatchSection psiCatchSection = (PsiCatchSection)SourceTreeToPsiMap.treeElementToPsi(catchSection);

    setupCatchBlock(exceptionName, context, psiCatchSection);
    return (PsiCatchSection)myManager.getCodeStyleManager().reformat(psiCatchSection);
  }

  private void setupCatchBlock(String exceptionName, PsiElement context, PsiCatchSection psiCatchSection)
    throws IncorrectOperationException {
    FileTemplate catchBodyTemplate = FileTemplateManager.getInstance().getCodeTemplate(FileTemplateManager.TEMPLATE_CATCH_BODY);
    LOG.assertTrue(catchBodyTemplate != null);

    Properties props = new Properties();
    props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION, exceptionName);
    if (context != null && context.isPhysical()) {
      FileTemplateUtil.setPackageNameAttribute(props, context.getContainingFile().getContainingDirectory());
    }
    PsiCodeBlock codeBlockFromText;
    try {
      String catchBody = catchBodyTemplate.getText(props);
      codeBlockFromText = createCodeBlockFromText("{\n" + catchBody + "\n}", null);
    }
    catch (Exception e) {
      throw new IncorrectOperationException("Incorrect file template");
    }
    psiCatchSection.getCatchBlock().replace(codeBlockFromText);
  }

  public XmlText createDisplayText(String s) throws IncorrectOperationException {
    final XmlTag tagFromText = createTagFromText("<a>" + XmlTagTextUtil.getCDATAQuote(s) + "</a>");
    final XmlText[] textElements = tagFromText.getValue().getTextElements();
    if (textElements.length == 0) return (XmlText)Factory.createCompositeElement(XmlElementType.XML_TEXT);
    return textElements[0];
  }

  public XmlTag createXHTMLTagFromText(String text) throws IncorrectOperationException {
    return ((XmlFile)createFileFromText("dummy.xhtml", text)).getDocument().getRootTag();
  }

  public PsiJavaCodeReferenceElement createPackageReferenceElement(String packageName)
    throws IncorrectOperationException {
    if (packageName.length() == 0) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReference(myManager, packageName);
  }

  public PsiReferenceExpression createReferenceExpression(PsiClass aClass) throws IncorrectOperationException {
    String text;
    if (aClass instanceof PsiAnonymousClass) {
      text = ((PsiAnonymousClass)aClass).getBaseClassType().getPresentableText();
    }
    else {
      text = aClass.getName();
    }
    return new LightClassReferenceExpression(myManager, text, aClass);
  }

  public PsiReferenceExpression createReferenceExpression(PsiPackage aPackage) throws IncorrectOperationException {
    if (aPackage.getQualifiedName().length() == 0) {
      throw new IncorrectOperationException("Cannot create reference to default package.");
    }
    return new LightPackageReferenceExpression(myManager, aPackage);
  }

  public PsiIdentifier createIdentifier(String text) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, text);
    return new LightIdentifier(myManager, text);
  }

  public PsiKeyword createKeyword(String text) throws IncorrectOperationException {
    if (!myManager.getNameHelper().isKeyword(text)) {
      throw new IncorrectOperationException("\"" + text + "\" is not a keyword.");
    }
    return new LightKeyword(myManager, text);
  }

  public PsiImportStatement createImportStatement(PsiClass aClass) throws IncorrectOperationException {
    if (aClass instanceof PsiAnonymousClass) {
      throw new IncorrectOperationException("Cannot create import statement for anonymous class.");
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) {
      throw new IncorrectOperationException("Cannot create import statement for local class.");
    }
    String text = "import " + aClass.getQualifiedName() + ";";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiImportStatement statement = aFile.getImportList().getImportStatements()[0];
    return (PsiImportStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  public PsiImportStatement createImportStatementOnDemand(String packageName) throws IncorrectOperationException {
    if (packageName.length() == 0) {
      throw new IncorrectOperationException("Cannot create import statement for default package.");
    }
    if (!myManager.getNameHelper().isQualifiedName(packageName)) {
      throw new IncorrectOperationException("Incorrect package name: \"" + packageName + "\".");
    }

    String text = "import " + packageName + ".*;";
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiImportStatement statement = aFile.getImportList().getImportStatements()[0];
    return (PsiImportStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
  }

  public PsiDeclarationStatement createVariableDeclarationStatement(String name, PsiType type, PsiExpression initializer, boolean reformat)
    throws IncorrectOperationException {
    if (!myManager.getNameHelper().isIdentifier(name)) {
      throw new IncorrectOperationException("\"" + name + "\" is not an identifier.");
    }
    if (type == PsiType.NULL) {
      throw new IncorrectOperationException("Cannot create field with type \"<null_type>\".");
    }
    StringBuffer buffer = new StringBuffer();
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

    if (reformat) {
      statement = (PsiDeclarationStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(statement);
    } 
    return statement;
    
  }

  public PsiDeclarationStatement createVariableDeclarationStatement(String name,
                                                                    PsiType type,
                                                                    PsiExpression initializer)
    throws IncorrectOperationException {
    return createVariableDeclarationStatement(name, type, initializer, true);
  }

  public PsiDocTag createParamTag(String parameterName, String description) throws IncorrectOperationException {
    StringBuffer buffer = new StringBuffer();
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

  public PsiDocTag createDocTagFromText(String docTagText, PsiElement context) throws IncorrectOperationException {
    StringBuffer buffer = new StringBuffer();
    buffer.append("/**\n");
    buffer.append(docTagText);
    buffer.append("\n */");
    PsiDocComment comment = createDocCommentFromText(buffer.toString(), context);
    return comment.getTags()[0];
  }

  public PsiDocComment createDocCommentFromText(String docCommentText, PsiElement context) throws IncorrectOperationException {
    StringBuffer buffer = new StringBuffer();
    buffer.append(docCommentText);
    buffer.append("void m();");
    final PsiMethod method = createMethodFromText(buffer.toString(), null);
    return method.getDocComment();
  }

  public PsiFile createFileFromText(String name, String text) throws IncorrectOperationException {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType type = fileTypeManager.getFileTypeByFileName(name);

    char[] chars = text.toCharArray();
    int startOffset = 0;
    int endOffset = text.length();

    if (type.isBinary()) {
      throw new IncorrectOperationException("Cannot create binary files from text");
    }

    return createFileFromText(myManager, type, name, chars, startOffset, endOffset);
  }

  public PsiFile createDummyFileFromText(final String name, FileType fileType, char[] chars, int startOffset, int endOffset) {
    LOG.assertTrue(!fileType.isBinary());
    return createFileFromText(myManager, fileType, name, chars, startOffset, endOffset);
  }

  public static PsiFile createFileFromText(PsiManagerImpl manager,
                                           FileType fileType,
                                           String name,
                                           char[] chars,
                                           int startOffset,
                                           int endOffset) {
    LOG.assertTrue(!fileType.isBinary());
    final Project project = manager.getProject();
    final CharArrayCharSequence text = new CharArrayCharSequence(chars, startOffset, endOffset);
    if (fileType instanceof LanguageFileType) {
      final ParserDefinition parserDefinition = ((LanguageFileType)fileType).getLanguage().getParserDefinition();
      if (parserDefinition != null) {
        return parserDefinition.createFile(project, name, text);
      }
    }
    return new PsiPlainTextFileImpl(project, name, fileType, text);
  }

  public PsiClass createClassFromText(String text, PsiElement context) throws IncorrectOperationException {
    String fileText = "class _Dummy_ { " + text + " }";
    PsiJavaFile aFile = createDummyJavaFile(fileText);
    PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException("Incorrect class \"" + text + "\".");
    }
    return classes[0];
  }

  public PsiField createFieldFromText(String text, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    TreeElement decl = getJavaParsingContext(holderElement).getDeclarationParsing().parseDeclarationText(myManager, myManager.getEffectiveLanguageLevel(), text.toCharArray(),
                                                               DeclarationParsing.Context.CLASS_CONTEXT);
    if (decl == null || decl.getElementType() != ElementType.FIELD) {
      throw new IncorrectOperationException("Incorrect field \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, decl);
    return (PsiField)SourceTreeToPsiMap.treeElementToPsi(decl);
  }

  private JavaParsingContext getJavaParsingContext (FileElement holderElement) {
    return new JavaParsingContext(holderElement.getCharTable(), myManager.getEffectiveLanguageLevel());
  }

  private JavaParsingContext getJavaParsingContext (FileElement holderElement, LanguageLevel languageLevel) {
    return new JavaParsingContext(holderElement.getCharTable(), languageLevel);
  }

  public PsiMethod createMethodFromText(String text, PsiElement context, LanguageLevel languageLevel) throws IncorrectOperationException {
    return createMethodFromTextInner(text, context, languageLevel);
  }

  public PsiMethod createMethodFromText(String text, PsiElement context) throws IncorrectOperationException {
    return createMethodFromTextInner(text, context, myManager.getEffectiveLanguageLevel());
  }

  private PsiMethod createMethodFromTextInner(String text, PsiElement context, LanguageLevel level) throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    TreeElement decl = getJavaParsingContext(holderElement, level).getDeclarationParsing().parseDeclarationText(myManager, level, text.toCharArray(),
                                                               DeclarationParsing.Context.CLASS_CONTEXT);
    if (decl == null || decl.getElementType() != ElementType.METHOD) {
      throw new IncorrectOperationException("Incorrect method \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, decl);
    return (PsiMethod)SourceTreeToPsiMap.treeElementToPsi(decl);
  }

  public PsiParameter createParameterFromText(String text, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    CompositeElement param = getJavaParsingContext(holderElement).getDeclarationParsing().parseParameterText(text.toCharArray());
    if (param == null) {
      throw new IncorrectOperationException("Incorrect parameter \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, param);
    return (PsiParameter)SourceTreeToPsiMap.treeElementToPsi(param);
  }

  public PsiType createTypeFromText(String text, PsiElement context) throws IncorrectOperationException {
    PsiPrimitiveType primitiveType = ourPrimitiveTypesMap.get(text);
    if (primitiveType != null) return primitiveType;
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    CompositeElement typeElement = Parsing.parseTypeText(myManager, text.toCharArray(), 0, text.length(), holderElement.getCharTable());
    if (typeElement == null) {
      throw new IncorrectOperationException("Incorrect type \"" + text + "\"");
    }
    TreeUtil.addChildren(holderElement, typeElement);
    PsiTypeElement psiTypeElement = (PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(typeElement);
    return psiTypeElement.getType();
  }

  public PsiCodeBlock createCodeBlockFromText(String text, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    CompositeElement treeElement = getJavaParsingContext(holderElement).getStatementParsing().parseCodeBlockText(myManager, text.toCharArray());
    if (treeElement == null) {
      throw new IncorrectOperationException("Incorrect code block \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, treeElement);
    return (PsiCodeBlock)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  public PsiStatement createStatementFromText(String text, PsiElement context) throws IncorrectOperationException {
    final FileElement treeHolder = new DummyHolder(myManager, context).getTreeElement();
    TreeElement treeElement = getJavaParsingContext(treeHolder).getStatementParsing().parseStatementText(text.toCharArray());
    if (treeElement == null) {
      throw new IncorrectOperationException("Incorrect statement \"" + text + "\".");
    }
    TreeUtil.addChildren(treeHolder, treeElement);
    return (PsiStatement)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  public PsiExpression createExpressionFromText(String text, PsiElement context) throws IncorrectOperationException {
    final FileElement treeHolder = new DummyHolder(myManager, context).getTreeElement();
    final CompositeElement treeElement = ExpressionParsing.parseExpressionText(myManager, text.toCharArray(), 0,
                                                                               text.length(), treeHolder.getCharTable());
    if (treeElement == null) {
      throw new IncorrectOperationException("Incorrect expression \"" + text + "\".");
    }
    TreeUtil.addChildren(treeHolder, treeElement);
    return (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  public PsiComment createCommentFromText(String text, PsiElement context) throws IncorrectOperationException {
    PsiJavaFile aFile = createDummyJavaFile(text);
    PsiElement[] children = aFile.getChildren();
    for (PsiElement aChildren : children) {
      if (aChildren instanceof PsiComment) {
        if (!aChildren.getText().equals(text)) {
          throw new IncorrectOperationException("Incorrect comment \"" + text + "\".");
        }
        PsiComment comment = (PsiComment)aChildren;
        new DummyHolder(myManager, (TreeElement)SourceTreeToPsiMap.psiElementToTree(comment), context);
        return comment;
      }
    }
    throw new IncorrectOperationException("Incorrect comment \"" + text + "\".");
  }

  private PsiJavaFile createDummyJavaFile(String text) throws IncorrectOperationException {
    String ext = StdFileTypes.JAVA.getDefaultExtension();
    return (PsiJavaFile)createFileFromText("_Dummy_." + ext, text);
  }

  private PsiAspectFile createDummyAspectFile(String text) throws IncorrectOperationException {
    String ext = StdFileTypes.ASPECT.getDefaultExtension();
    return (PsiAspectFile)createFileFromText("_Dummy_." + ext, text);
  }

  public XmlTag createTagFromText(String text) throws IncorrectOperationException {
    return ((XmlFile)createFileFromText("dummy.xml", text)).getDocument().getRootTag();
  }

  public XmlAttribute createXmlAttribute(String name, String value) throws IncorrectOperationException {
    XmlTag tag = ((XmlFile)createFileFromText("dummy.xml", "<tag " + name + "=\"" + value + "\"/>")).getDocument()
      .getRootTag();
    return tag.getAttributes()[0];
  }

  public PsiTypePattern createTypePattern(String pattern) throws IncorrectOperationException {
    PsiAspectFile psiFile = createDummyAspectFile("aspect foo { pointcut foo():within(" + pattern + ");}");
    PsiWithinPointcut pointcut = (PsiWithinPointcut)psiFile.getAspects()[0].getPointcutDefs()[0].getPointcut();
    return pointcut.getTypePattern();
  }

  public PsiExpressionCodeFragment createExpressionCodeFragment(String text,
                                                                PsiElement context,
                                                                final PsiType expectedType,
                                                                boolean isPhysical) {
    final PsiExpressionCodeFragmentImpl result = new PsiExpressionCodeFragmentImpl(
    myManager.getProject(), isPhysical, "fragment.java", text, expectedType);
    result.setContext(context);
    return result;
  }

  public PsiCodeFragment createCodeBlockCodeFragment(String text, PsiElement context, boolean isPhysical) {
    final PsiCodeFragmentImpl result = new PsiCodeFragmentImpl(myManager.getProject(),
                                                               ElementType.STATEMENTS,
                                                               isPhysical,
                                                               "fragment.java",
                                                               text);
    result.setContext(context);
    return result;
  }

  public PsiTypeCodeFragment createTypeCodeFragment(String text, PsiElement context, boolean isPhysical) {

    return createTypeCodeFragment(text, context, false, isPhysical, false);
  }


  public PsiTypeCodeFragment createTypeCodeFragment(String text,
                                                    PsiElement context,
                                                    boolean isVoidValid,
                                                    boolean isPhysical) {
    return createTypeCodeFragment(text, context, true, isPhysical, false);
  }

  public PsiTypeCodeFragment createTypeCodeFragment(String text,
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


  public PsiTypeParameter createTypeParameterFromText(String text, PsiElement context)
    throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    TreeElement treeElement = getJavaParsingContext(holderElement).getDeclarationParsing().parseTypeParameterText(text.toCharArray());
    if (treeElement == null) {
      throw new IncorrectOperationException("Incorrect type parameter \"" + text + "\"");
    }
    TreeUtil.addChildren(holderElement, treeElement);
    return (PsiTypeParameter)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

}
