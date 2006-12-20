package com.intellij.psi.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.light.*;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
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
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.HashMap;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharSequenceSubSequence;
import com.intellij.xml.util.XmlTagTextUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public class PsiElementFactoryImpl implements PsiElementFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiElementFactoryImpl");

  private PsiClass ARRAY_CLASS;
  private PsiClass ARRAY_CLASS15;

  private final PsiManagerImpl myManager;
  private static final Map<String, PsiPrimitiveType> ourPrimitiveTypesMap = new HashMap<String, PsiPrimitiveType>();

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

  private PsiJavaFile myDummyJavaFile;

  public PsiElementFactoryImpl(PsiManagerImpl manager) {
    myManager = manager;
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
  public PsiEnumConstant createEnumConstantFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    TreeElement decl = getJavaParsingContext(holderElement).getDeclarationParsing().parseEnumConstantText(myManager, text);
    if (decl == null || decl.getElementType() != JavaElementType.ENUM_CONSTANT) {
      throw new IncorrectOperationException("Incorrect enum constant text \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, decl);
    holderElement.acceptTree(new GeneratedMarkerVisitor());
    return (PsiEnumConstant)SourceTreeToPsiMap.treeElementToPsi(decl);
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
    getJavaParsingContext(treeHolder).getDeclarationParsing().parseParameterText((PsiKeyword.INT + " " + name));
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

  @NotNull
  public PsiFile createFileFromText(@NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text,
                                    long modificationStamp, final boolean physical) {
    return createFileFromText(name, fileType, text, modificationStamp, physical, true);
  }

  @NotNull
  public PsiFile createFileFromText(@NotNull String name,
                                    @NotNull FileType fileType,
                                    @NotNull CharSequence text,
                                    long modificationStamp,
                                    final boolean physical,
                                    boolean markAsCopy) {
    final LightVirtualFile virtualFile = new LightVirtualFile(name, fileType, text, modificationStamp);

    if(fileType instanceof LanguageFileType){
      final Language language = ((LanguageFileType)fileType).getLanguage();
      final ParserDefinition parserDefinition = language.getParserDefinition();
      FileViewProvider viewProvider = language.createViewProvider(virtualFile, myManager, physical);
      if (viewProvider == null) viewProvider = new SingleRootFileViewProvider(myManager, virtualFile, physical);
      if (parserDefinition != null){
        final PsiFile psiFile = viewProvider.getPsi(language);
        if(markAsCopy) ((TreeElement)psiFile.getNode()).acceptTree(new GeneratedMarkerVisitor());
        return psiFile;
      }
    }
    final SingleRootFileViewProvider singleRootFileViewProvider =
      new SingleRootFileViewProvider(myManager, virtualFile, physical);
    final PsiPlainTextFileImpl plainTextFile = new PsiPlainTextFileImpl(singleRootFileViewProvider);
    if(markAsCopy) CodeEditUtil.setNodeGenerated(plainTextFile.getNode(), true);
    return plainTextFile;
  }

  @NotNull
  public PsiFile createFileFromText(@NotNull String name,
                                    @NotNull FileType fileType,
                                    @NotNull Language lang,
                                    @NotNull CharSequence text,
                                    long modificationStamp,
                                    final boolean physical,
                                    boolean markAsCopy) {
    final LightVirtualFile virtualFile = new LightVirtualFile(name, fileType, text, modificationStamp);

    if(fileType instanceof LanguageFileType){
      final Language language = ((LanguageFileType)fileType).getLanguage();
      final ParserDefinition parserDefinition = language.getParserDefinition();
      FileViewProvider viewProvider = language.createViewProvider(virtualFile, myManager, physical);
      if (viewProvider == null) viewProvider = new SingleRootFileViewProvider(myManager, virtualFile, physical);
      if (parserDefinition != null){
        final PsiFile psiFile = viewProvider.getPsi(lang);
        if(markAsCopy) ((TreeElement)psiFile.getNode()).acceptTree(new GeneratedMarkerVisitor());
        return psiFile;
      }
    }
    final SingleRootFileViewProvider singleRootFileViewProvider =
      new SingleRootFileViewProvider(myManager, virtualFile, physical);
    final PsiPlainTextFileImpl plainTextFile = new PsiPlainTextFileImpl(singleRootFileViewProvider);
    if(markAsCopy) CodeEditUtil.setNodeGenerated(plainTextFile.getNode(), true);
    return plainTextFile;
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
    return javaFile.getPackageStatement();
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
  public PsiAnnotation createAnnotationFromText(@NotNull String annotationText, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    CompositeElement annotationElement =
    getJavaParsingContext(holderElement).getDeclarationParsing().parseAnnotationFromText(myManager, annotationText, getLanguageLevel(context));
    if (annotationElement == null || annotationElement.getElementType() != JavaElementType.ANNOTATION) {
      throw new IncorrectOperationException("Incorrect annotation \"" + annotationText + "\".");
    }
    TreeUtil.addChildren(holderElement, annotationElement);
    annotationElement.acceptTree(new GeneratedMarkerVisitor());
    return (PsiAnnotation)SourceTreeToPsiMap.treeElementToPsi(annotationElement);
  }

  private LanguageLevel getLanguageLevel(final PsiElement context) {
    if (context == null) return myManager.getEffectiveLanguageLevel();
    return PsiUtil.getLanguageLevel(context);
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
  public PsiCatchSection createCatchSection(@NotNull PsiClassType exceptionType,
                                            @NotNull String exceptionName,
                                            PsiElement context) throws IncorrectOperationException {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append("catch (");
    buffer.append(exceptionType.getCanonicalText());
    buffer.append(" ").append(exceptionName).append("){}");
    String catchSectionText = buffer.toString();
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    TreeElement catchSection = getJavaParsingContext(holderElement).getStatementParsing().parseCatchSectionText(catchSectionText);
    LOG.assertTrue(catchSection != null && catchSection.getElementType() == JavaElementType.CATCH_SECTION);
    TreeUtil.addChildren(holderElement, catchSection);
    PsiCatchSection psiCatchSection = (PsiCatchSection)SourceTreeToPsiMap.treeElementToPsi(catchSection);

    setupCatchBlock(exceptionName, context, psiCatchSection);
    holderElement.acceptTree(new GeneratedMarkerVisitor());
    return (PsiCatchSection)myManager.getCodeStyleManager().reformat(psiCatchSection);
  }

  private void setupCatchBlock(String exceptionName, PsiElement context, PsiCatchSection psiCatchSection)
    throws IncorrectOperationException {
    FileTemplate catchBodyTemplate = FileTemplateManager.getInstance().getCodeTemplate(FileTemplateManager.TEMPLATE_CATCH_BODY);
    LOG.assertTrue(catchBodyTemplate != null);

    Properties props = new Properties();
    props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION, exceptionName);
    if (context != null && context.isPhysical()) {
      PsiDirectory directory = context.getContainingFile().getContainingDirectory();
      if (directory != null) {
        FileTemplateUtil.setPackageNameAttribute(props, directory);
      }
    }
    PsiCodeBlock codeBlockFromText;
    try {
      String catchBody = catchBodyTemplate.getText(props);
      codeBlockFromText = createCodeBlockFromText("{\n" + catchBody + "\n}", null);
    }
    catch (ProcessCanceledException ce) {
      throw ce;
    }
    catch (Exception e) {
      throw new IncorrectOperationException("Incorrect file template",e);
    }
    psiCatchSection.getCatchBlock().replace(codeBlockFromText);
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
  public PsiElement createWhiteSpaceFromText(@NotNull @NonNls String text) throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, null).getTreeElement();
    final LeafElement newElement = Factory.createLeafElement(
      TokenType.WHITE_SPACE,
      text,
      0,
      text.length(),
      -1,
      holderElement.getCharTable()
    );
    TreeUtil.addChildren(holderElement, newElement);
    holderElement.acceptTree(new GeneratedMarkerVisitor());
    return newElement.getPsi();
  }

  @NotNull
  public PsiFile createFileFromText(@NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text) {
    return createFileFromText(name, fileType, text, LocalTimeCounter.currentTime(), false);
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
    ((TreeElement)statement.getNode()).acceptTree(new GeneratedMarkerVisitor());
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
  public PsiDocTag createDocTagFromText(@NotNull String docTagText, PsiElement context) throws IncorrectOperationException {
    StringBuilder buffer = new StringBuilder();
    buffer.append("/**\n");
    buffer.append(docTagText);
    buffer.append("\n */");
    PsiDocComment comment = createDocCommentFromText(buffer.toString(), context);
    return comment.getTags()[0];
  }

  @NotNull
  public PsiDocComment createDocCommentFromText(@NotNull String docCommentText, PsiElement context) throws IncorrectOperationException {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append(docCommentText);
    buffer.append("void m();");
    final PsiMethod method = createMethodFromText(buffer.toString(), null);
    return method.getDocComment();
  }

  @NotNull
  public PsiFile createFileFromText(@NotNull String name, @NotNull String text){
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType type = fileTypeManager.getFileTypeByFileName(name);
    if (type.isBinary()) {
      throw new RuntimeException("Cannot create binary files from text");
    }

    return createFileFromText(name, type, text);
  }

  private PsiFile createFileFromText(FileType fileType, final String fileName, CharSequence chars, int startOffset, int endOffset) {
    LOG.assertTrue(!fileType.isBinary());
    final CharSequence text = startOffset == 0 && endOffset == chars.length()?chars:new CharSequenceSubSequence(chars, startOffset, endOffset);
    return createFileFromText(fileName, fileType, text);
  }

  @NotNull
  public PsiClass createClassFromText(@NotNull String body, PsiElement context) throws IncorrectOperationException {
    @NonNls String fileText = "class _Dummy_ { " + body + " }";
    PsiJavaFile aFile = createDummyJavaFile(fileText);
    PsiClass[] classes = aFile.getClasses();
    if (classes.length != 1) {
      throw new IncorrectOperationException("Incorrect class \"" + body + "\".");
    }
    return classes[0];
  }

  @NotNull
  public PsiField createFieldFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    TreeElement decl = getJavaParsingContext(holderElement).getDeclarationParsing().parseDeclarationText(myManager, myManager.getEffectiveLanguageLevel(), text,
                                                                                                         DeclarationParsing.Context.CLASS_CONTEXT);
    if (decl == null || decl.getElementType() != JavaElementType.FIELD) {
      throw new IncorrectOperationException("Incorrect field \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, decl);
    holderElement.acceptTree(new GeneratedMarkerVisitor());
    return (PsiField)SourceTreeToPsiMap.treeElementToPsi(decl);
  }

  private JavaParsingContext getJavaParsingContext (FileElement holderElement) {
    return new JavaParsingContext(holderElement.getCharTable(), myManager.getEffectiveLanguageLevel());
  }

  private static JavaParsingContext getJavaParsingContext (FileElement holderElement, LanguageLevel languageLevel) {
    return new JavaParsingContext(holderElement.getCharTable(), languageLevel);
  }

  @NotNull
  public PsiMethod createMethodFromText(@NotNull String text, PsiElement context, LanguageLevel languageLevel) throws IncorrectOperationException {
    return createMethodFromTextInner(text, context, languageLevel);
  }

  @NotNull
  public PsiMethod createMethodFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    return createMethodFromTextInner(text, context, myManager.getEffectiveLanguageLevel());
  }

  private PsiMethod createMethodFromTextInner(String text, PsiElement context, LanguageLevel level) throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    TreeElement decl = getJavaParsingContext(holderElement, level).getDeclarationParsing().parseDeclarationText(myManager, level, text,
                                                                                                                DeclarationParsing.Context.CLASS_CONTEXT);
    if (decl == null || decl.getElementType() != JavaElementType.METHOD) {
      throw new IncorrectOperationException("Incorrect method \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, decl);
    holderElement.acceptTree(new GeneratedMarkerVisitor());
    return (PsiMethod)SourceTreeToPsiMap.treeElementToPsi(decl);
  }

  @NotNull
  public PsiParameter createParameterFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    CompositeElement param = getJavaParsingContext(holderElement).getDeclarationParsing().parseParameterText(text);
    if (param == null) {
      throw new IncorrectOperationException("Incorrect parameter \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, param);
    holderElement.acceptTree(new GeneratedMarkerVisitor());
    return (PsiParameter)SourceTreeToPsiMap.treeElementToPsi(param);
  }

  @NotNull
  public PsiType createTypeFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    PsiPrimitiveType primitiveType = ourPrimitiveTypesMap.get(text);
    if (primitiveType != null) return primitiveType;
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    CompositeElement typeElement = Parsing.parseTypeText(myManager, text, 0, text.length(), holderElement.getCharTable());
    if (typeElement == null) {
      throw new IncorrectOperationException("Incorrect type \"" + text + "\"");
    }
    TreeUtil.addChildren(holderElement, typeElement);
    PsiTypeElement psiTypeElement = (PsiTypeElement)SourceTreeToPsiMap.treeElementToPsi(typeElement);
    if (psiTypeElement == null) {
      throw new IncorrectOperationException("PSI is null for element "+typeElement);
    }
    holderElement.acceptTree(new GeneratedMarkerVisitor());
    return psiTypeElement.getType();
  }

  @NotNull
  public PsiCodeBlock createCodeBlockFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    CompositeElement treeElement = getJavaParsingContext(holderElement).getStatementParsing().parseCodeBlockText(myManager, text);
    if (treeElement == null) {
      throw new IncorrectOperationException("Incorrect code block \"" + text + "\".");
    }
    TreeUtil.addChildren(holderElement, treeElement);
    holderElement.acceptTree(new GeneratedMarkerVisitor());
    return (PsiCodeBlock)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  @NotNull
  public PsiStatement createStatementFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    final FileElement treeHolder = new DummyHolder(myManager, context).getTreeElement();
    TreeElement treeElement = getJavaParsingContext(treeHolder).getStatementParsing().parseStatementText(text);
    if (treeElement == null) {
      throw new IncorrectOperationException("Incorrect statement \"" + text + "\".");
    }
    TreeUtil.addChildren(treeHolder, treeElement);
    treeHolder.acceptTree(new GeneratedMarkerVisitor());
    return (PsiStatement)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  @NotNull
  public PsiExpression createExpressionFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
    final FileElement treeHolder = new DummyHolder(myManager, context).getTreeElement();
    final CompositeElement treeElement = ExpressionParsing.parseExpressionText(myManager, text, 0,
                                                                               text.length(), treeHolder.getCharTable());
    if (treeElement == null) {
      throw new IncorrectOperationException("Incorrect expression \"" + text + "\".");
    }
    TreeUtil.addChildren(treeHolder, treeElement);
    treeHolder.acceptTree(new GeneratedMarkerVisitor());
    return (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

  @NotNull
  public PsiComment createCommentFromText(@NotNull String text, PsiElement context) throws IncorrectOperationException {
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

  private PsiJavaFile createDummyJavaFile(String text) {
    String ext = StdFileTypes.JAVA.getDefaultExtension();
    @NonNls String fileName = "_Dummy_." + ext;
    FileType type = StdFileTypes.JAVA;

    return (PsiJavaFile) createFileFromText(type, fileName, text, 0, text.length());
  }

  @NotNull
  public XmlTag createTagFromText(@NotNull String text) throws IncorrectOperationException {
    final XmlTag tag = ((XmlFile)createFileFromText("dummy.xml", text)).getDocument().getRootTag();
    if (tag == null) throw new IncorrectOperationException("Incorrect tag text");
    return tag;
  }

  @NotNull
  public XmlAttribute createXmlAttribute(@NotNull String name, String value) throws IncorrectOperationException {
    XmlTag tag = ((XmlFile)createFileFromText("dummy.xml", "<tag " + name + "=\"" + value + "\"/>")).getDocument()
      .getRootTag();
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
  public PsiTypeParameter createTypeParameterFromText(@NotNull String text, PsiElement context)
    throws IncorrectOperationException {
    final FileElement holderElement = new DummyHolder(myManager, context).getTreeElement();
    TreeElement treeElement = getJavaParsingContext(holderElement).getDeclarationParsing().parseTypeParameterText(text);
    if (treeElement == null) {
      throw new IncorrectOperationException("Incorrect type parameter \"" + text + "\"");
    }
    TreeUtil.addChildren(holderElement, treeElement);
    holderElement.acceptTree(new GeneratedMarkerVisitor());
    return (PsiTypeParameter)SourceTreeToPsiMap.treeElementToPsi(treeElement);
  }

}
