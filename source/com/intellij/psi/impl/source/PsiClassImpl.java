package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.PomMemberOwner;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.ClassView;
import com.intellij.psi.impl.cache.RepositoryElementType;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.impl.source.tree.java.ClassElement;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterListImpl;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PsiClassImpl extends NonSlaveRepositoryPsiElement implements PsiClass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiClassImpl");

  private PsiModifierListImpl myRepositoryModifierList = null;
  private PsiReferenceListImpl myRepositoryExtendsList = null;
  private PsiReferenceListImpl myRepositoryImplementsList = null;
  private PsiTypeParameterListImpl myRepositoryParameterList = null;

  private String myCachedName = null;
  private String myCachedQName = null;
  private Map<String, PsiField> myCachedFieldsMap = null;
  private Map<String, PsiMethod[]> myCachedMethodsMap = null;
  private Map<String, PsiClass> myCachedInnersMap = null;

  private PsiField[] myCachedFields = null;
  private PsiMethod[] myCachedMethods = null;
  private PsiMethod[] myCachedConstructors = null;
  private PsiClass[] myCachedInners = null;
  private Boolean myCachedIsDeprecated = null;
  private Boolean myCachedIsInterface = null;
  private Boolean myCachedIsAnnotationType = null;
  private Boolean myCachedIsEnum = null;

  private PsiMethod myValuesMethod = null;
  private PsiMethod myValueOfMethod = null;
  private String myCachedForLongName = null;
  private static final @NonNls String VALUES_METHOD = "values";
  private static final @NonNls String VALUE_OF_METHOD = "valueOf";

  public PsiClassImpl(PsiManagerImpl manager, long repositoryId) {
    super(manager, repositoryId);
  }

  public PsiClassImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public void subtreeChanged() {
    dropCaches();

    super.subtreeChanged();
  }

  private void dropCaches() {
    myCachedName = null;
    myCachedFields = null;
    myCachedMethods = null;
    myCachedConstructors = null;
    myCachedInners = null;

    myCachedFieldsMap = null;
    myCachedMethodsMap = null;
    myCachedInnersMap = null;

    myCachedIsDeprecated = null;
    myCachedIsInterface = null;
    myCachedIsAnnotationType = null;
    myCachedIsEnum = null;
    myCachedQName = null;
    myCachedForLongName = null;
  }

  protected Object clone() {
    PsiClassImpl clone = (PsiClassImpl)super.clone();
    clone.myRepositoryModifierList = null;
    clone.myRepositoryExtendsList = null;
    clone.myRepositoryImplementsList = null;
    clone.myRepositoryParameterList = null;

    clone.dropCaches();

    return clone;
  }

  public void setRepositoryId(long repositoryId) {
    super.setRepositoryId(repositoryId);

    if (repositoryId < 0){
      if (myRepositoryModifierList != null){
        myRepositoryModifierList.setOwner(this);
        myRepositoryModifierList = null;
      }
      if (myRepositoryExtendsList != null){
        myRepositoryExtendsList.setOwner(this);
        myRepositoryExtendsList = null;
      }
      if (myRepositoryImplementsList != null){
        myRepositoryImplementsList.setOwner(this);
        myRepositoryImplementsList = null;
      }
      if (myRepositoryParameterList != null) {
        myRepositoryParameterList.setOwner(this);
        myRepositoryParameterList = null;
      }
    }
    else{
      myRepositoryModifierList = (PsiModifierListImpl)bindSlave(ChildRole.MODIFIER_LIST);
      myRepositoryExtendsList = (PsiReferenceListImpl)bindSlave(ChildRole.EXTENDS_LIST);
      myRepositoryImplementsList = (PsiReferenceListImpl)bindSlave(ChildRole.IMPLEMENTS_LIST);
      myRepositoryParameterList = (PsiTypeParameterListImpl)bindSlave(ChildRole.TYPE_PARAMETER_LIST);
    }

    dropCaches();
  }

  public PsiElement getParent() {
    PsiElement parent = getDefaultParentByRepository();
    if (parent instanceof PsiMethod || parent instanceof PsiField || parent instanceof PsiClassInitializer){
      return SourceTreeToPsiMap.treeElementToPsi(calcTreeElement().getTreeParent()); // anonymous or local
    }
    return parent;
  }

  public PsiElement getOriginalElement() {
    PsiFile psiFile = getContainingFile();
    VirtualFile vFile = psiFile.getVirtualFile();
    final ProjectFileIndex idx = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex();

    if (!idx.isInLibrarySource(vFile)) return this;
    final Set<OrderEntry> orderEntries = new HashSet<OrderEntry>(idx.getOrderEntriesForFile(vFile));
    PsiClass original = myManager.findClass(getQualifiedName(), new GlobalSearchScope() {
      public int compare(VirtualFile file1, VirtualFile file2) {
        return 0;
      }

      public boolean contains(VirtualFile file) {
        // order for file and vFile has non empty intersection.
        Set<OrderEntry> entries = new HashSet<OrderEntry>(idx.getOrderEntriesForFile(file));
        int size = entries.size();
        entries.addAll(orderEntries);
        return size + orderEntries.size() > entries.size();
      }

      public boolean isSearchInModuleContent(Module aModule) {
        return false;
      }

      public boolean isSearchInLibraries() {
        return true;
      }
    });

    return original != null ? original : this;
  }

  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  public PsiElement getScope() {
    ASTNode treeElement = getTreeElement();
    if (treeElement != null){
      ASTNode parent = treeElement.getTreeParent();
      while(true){
        if (parent instanceof RepositoryTreeElement){
          return SourceTreeToPsiMap.treeElementToPsi(parent);
        }
        parent = parent.getTreeParent();
      }
    }
    else{
        return getRepositoryElementsManager().findOrCreatePsiElementById(getParentId());
      }
    }

  public String getName() {
      if (myCachedName == null){
        if (getTreeElement() != null){
          PsiIdentifier identifier = getNameIdentifier();
          myCachedName = identifier != null ? identifier.getText() : null;
        }
        else{
          myCachedName = getRepositoryManager().getClassView().getName(getRepositoryId());
        }
      }
      return myCachedName;
    }

  public String getQualifiedName() {
    String qName = myCachedQName;
    if (qName == null) {
      if (getTreeElement() != null) {
        PsiElement parent = getParent();
        if (parent instanceof PsiJavaFile) {
          String packageName = ((PsiJavaFile)parent).getPackageName();
          if (packageName.length() > 0) {
            qName = packageName + "." + getName();
          }
          else {
            qName = getName();
          }
        }
        else if (parent instanceof PsiClass) {
          String parentQName = ((PsiClass)parent).getQualifiedName();
          if (parentQName == null) return null;
          qName = parentQName + "." + getName();
        }
      }
      else {
        qName = getRepositoryManager().getClassView().getQualifiedName(getRepositoryId());
      }
      if (getRepositoryId() >= 0) {
        myCachedQName = qName;
      }
    }
    return qName;
  }

  public PsiModifierList getModifierList(){
    long repositoryId = getRepositoryId();
    if (repositoryId >= 0){
      if (myRepositoryModifierList == null){
        myRepositoryModifierList = new PsiModifierListImpl(myManager, this);
      }
      return myRepositoryModifierList;
    }
    else{
      return (PsiModifierList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.MODIFIER_LIST);
    }
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  public PsiReferenceList getExtendsList() {
    if (isEnum() || isAnnotationType()) return null;
    long repositoryId = getRepositoryId();
    if (repositoryId >= 0){
      if (myRepositoryExtendsList == null){
        myRepositoryExtendsList = new PsiReferenceListImpl(myManager, this, JavaElementType.EXTENDS_LIST);
      }
      return myRepositoryExtendsList;
    }
    else{
      return (PsiReferenceList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.EXTENDS_LIST);
    }
  }

  public PsiReferenceList getImplementsList() {
    long repositoryId = getRepositoryId();
    if (repositoryId >= 0){
      if (myRepositoryImplementsList == null){
        myRepositoryImplementsList = new PsiReferenceListImpl(myManager, this, JavaElementType.IMPLEMENTS_LIST);
      }
      return myRepositoryImplementsList;
    }
    else{
      return (PsiReferenceList)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.IMPLEMENTS_LIST);
    }
  }

  @NotNull
  public PsiClassType[] getExtendsListTypes() {
    return PsiClassImplUtil.getExtendsListTypes(this);
  }

  @NotNull
  public PsiClassType[] getImplementsListTypes() {
    return PsiClassImplUtil.getImplementsListTypes(this);
  }

  public PsiClass getSuperClass() {
    return PsiClassImplUtil.getSuperClass(this);
  }

  public PsiClass[] getInterfaces() {
    return PsiClassImplUtil.getInterfaces(this);
  }

  @NotNull
  public PsiClass[] getSupers() {
    return PsiClassImplUtil.getSupers(this);
  }

  @NotNull
  public PsiClassType[] getSuperTypes() {
    return PsiClassImplUtil.getSuperTypes(this);
  }

  @Nullable
  public PsiClass getContainingClass() {
    PsiElement parent = getDefaultParentByRepository();
    return parent instanceof PsiClass ? (PsiClass)parent : null;
  }

  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  @NotNull
  public PsiField[] getFields() {
    PsiField[] cachedFields = myCachedFields;
    if (cachedFields == null){
        if (getTreeElement() != null){
          cachedFields = calcTreeElement().getChildrenAsPsiElements(FIELD_BIT_SET, PSI_FIELD_ARRAY_CONSTRUCTOR);
        }
        else{
          long[] fieldIds = getRepositoryManager().getClassView().getFields(getRepositoryId());
          PsiField[] fields = fieldIds.length > 0 ? new PsiField[fieldIds.length] : PsiField.EMPTY_ARRAY;
          for(int i = 0; i < fieldIds.length; i++){
            long id = fieldIds[i];
            fields[i] = (PsiField)getRepositoryElementsManager().findOrCreatePsiElementById(id);
          }
          cachedFields = fields;
        }
        myCachedFields = cachedFields;
      }
      return cachedFields;
    }

  @NotNull
  public PsiMethod[] getMethods() {
      if (myCachedMethods == null){
        if (getTreeElement() != null){
          myCachedMethods = calcTreeElement().getChildrenAsPsiElements(METHOD_BIT_SET, PSI_METHOD_ARRAY_CONSTRUCTOR);
        }
        else{
          long[] methodIds = getRepositoryManager().getClassView().getMethods(getRepositoryId());
          PsiMethod[] methods = methodIds.length > 0 ? new PsiMethod[methodIds.length] : PsiMethod.EMPTY_ARRAY;
          for(int i = 0; i < methodIds.length; i++){
            long id = methodIds[i];
            methods[i] = (PsiMethod)getRepositoryElementsManager().findOrCreatePsiElementById(id);
          }
          myCachedMethods = methods;
        }
      }
      return myCachedMethods;
    }

  @NotNull
  public PsiMethod[] getConstructors() {
    if (myCachedConstructors == null) {
      myCachedConstructors = PsiImplUtil.getConstructors(this);
    }
    return myCachedConstructors;
  }

  @NotNull
  public PsiClass[] getInnerClasses() {
    PsiClass[] cachedInners = myCachedInners;
    if (cachedInners == null) {
      if (getTreeElement() != null) {
        cachedInners = calcTreeElement().getChildrenAsPsiElements(CLASS_BIT_SET, PSI_CLASS_ARRAY_CONSTRUCTOR);
      }
      else {
        long[] classIds = getRepositoryManager().getClassView().getInnerClasses(getRepositoryId());
        PsiClass[] classes = classIds.length > 0 ? new PsiClass[classIds.length] : PsiClass.EMPTY_ARRAY;
        for (int i = 0; i < classIds.length; i++) {
          long id = classIds[i];
          classes[i] = (PsiClass)getRepositoryElementsManager().findOrCreatePsiElementById(id);
        }
        cachedInners = classes;
      }
      myCachedInners = cachedInners;
    }
    return cachedInners;
  }

  @NotNull
  public PsiClassInitializer[] getInitializers() {
    if (getTreeElement() != null) {
      return calcTreeElement().getChildrenAsPsiElements(CLASS_INITIALIZER_BIT_SET, PSI_CLASS_INITIALIZER_ARRAY_CONSTRUCTOR);
    }
    else {
      long[] initializerIds = getRepositoryManager().getClassView().getInitializers(getRepositoryId());
      PsiClassInitializer[] initializers = initializerIds.length > 0 ?
                                           new PsiClassInitializer[initializerIds.length] : PsiClassInitializer.EMPTY_ARRAY;
      for (int i = 0; i < initializerIds.length; i++) {
        long id = initializerIds[i];
        initializers[i] = (PsiClassInitializer)getRepositoryElementsManager().findOrCreatePsiElementById(id);
      }
      return initializers;
    }
  }

  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  @NotNull
  public PsiField[] getAllFields() {
    return PsiClassImplUtil.getAllFields(this);
  }

  @NotNull
  public PsiMethod[] getAllMethods() {
    return PsiClassImplUtil.getAllMethods(this);
  }

  @NotNull
  public PsiClass[] getAllInnerClasses() {
    return PsiClassImplUtil.getAllInnerClasses(this);
  }

  public PsiField findFieldByName(String name, boolean checkBases) {
    if(!checkBases){
      Map<String, PsiField> cachedFields = myCachedFieldsMap;
      if(cachedFields == null){
        final PsiField[] fields = getFields();
        cachedFields = new HashMap<String, PsiField>();
        for (final PsiField field : fields) {
          cachedFields.put(field.getName(), field);
        }
        myCachedFieldsMap = cachedFields;
      }
      return cachedFields.get(name);
    }
    return PsiClassImplUtil.findFieldByName(this, name, checkBases);
  }

  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases);
  }

  @NotNull
  public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
    if(!checkBases){
      Map<String, PsiMethod[]> cachedMethods = myCachedMethodsMap;
      if(cachedMethods == null){
        cachedMethods = new HashMap<String,PsiMethod[]>();

        Map<String, List<PsiMethod>> cachedMethodsMap = new HashMap<String,List<PsiMethod>>();
        final PsiMethod[] methods = getMethods();
        for (final PsiMethod method : methods) {
          List<PsiMethod> list = cachedMethodsMap.get(method.getName());
          if (list == null) {
            list = new ArrayList<PsiMethod>(1);
            cachedMethodsMap.put(method.getName(), list);
          }
          list.add(method);
        }
        for (final String methodName : cachedMethodsMap.keySet()) {
          cachedMethods.put(methodName, cachedMethodsMap.get(methodName).toArray(PsiMethod.EMPTY_ARRAY));
        }
        myCachedMethodsMap = cachedMethods;
      }

      final PsiMethod[] psiMethods = cachedMethods.get(name);
      return psiMethods != null ? psiMethods : PsiMethod.EMPTY_ARRAY;
    }

    return PsiClassImplUtil.findMethodsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String name, boolean checkBases) {
    return PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @NotNull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return PsiClassImplUtil.getAllWithSubstitutorsByMap(this, PsiMethod.class);
  }

  public PsiClass findInnerClassByName(String name, boolean checkBases) {
    if(!checkBases){
      Map<String, PsiClass> inners = myCachedInnersMap;
      if(inners == null){
        final PsiClass[] classes = getInnerClasses();
        inners = new HashMap<String,PsiClass>();
        for (final PsiClass psiClass : classes) {
          inners.put(psiClass.getName(), psiClass);
        }
        myCachedInnersMap = inners;
      }
      return inners.get(name);
    }
    return PsiClassImplUtil.findInnerByName(this, name, checkBases);
  }

  public PsiTypeParameterList getTypeParameterList() {
    long repositoryId = getRepositoryId();
    if (repositoryId >= 0){
      if (myRepositoryParameterList == null){
        myRepositoryParameterList = new PsiTypeParameterListImpl(myManager, this);
      }
      return myRepositoryParameterList;
    }

    return (PsiTypeParameterList) calcTreeElement().findChildByRoleAsPsiElement(ChildRole.TYPE_PARAMETER_LIST);
  }

  public boolean hasTypeParameters() {
    final PsiTypeParameterList typeParameterList = getTypeParameterList();
    return typeParameterList != null && typeParameterList.getTypeParameters().length != 0;
  }

  public boolean isDeprecated() {
    Boolean cachedIsDeprecated = myCachedIsDeprecated;
    if (cachedIsDeprecated == null) {
      boolean deprecated;
      if (getTreeElement() != null) {
        PsiDocComment docComment = getDocComment();
        deprecated = docComment != null && docComment.findTagByName("deprecated") != null;
        if (!deprecated) {
          PsiModifierList modifierList = getModifierList();
          if (modifierList != null) {
            deprecated = modifierList.findAnnotation("java.lang.Deprecated") != null;
          }
        }
      }
      else {
        ClassView classView = getRepositoryManager().getClassView();
        deprecated = classView.isDeprecated(getRepositoryId());
        if (!deprecated && classView.mayBeDeprecatedByAnnotation(getRepositoryId())) {
          deprecated = getModifierList().findAnnotation("java.lang.Deprecated") != null;
        }
      }
      cachedIsDeprecated = myCachedIsDeprecated = deprecated ? Boolean.TRUE : Boolean.FALSE;
    }
    return cachedIsDeprecated.booleanValue();
  }

  public PsiDocComment getDocComment(){
    return (PsiDocComment)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
  }

  public PsiJavaToken getLBrace() {
    return (PsiJavaToken)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.LBRACE);
  }

  public PsiJavaToken getRBrace() {
    return (PsiJavaToken)calcTreeElement().findChildByRoleAsPsiElement(ChildRole.RBRACE);
  }

  public boolean isInterface() {
    Boolean cachedIsInterface = myCachedIsInterface;
    if (cachedIsInterface == null) {
      boolean isInterface;
      if (getTreeElement() != null) {
        ASTNode keyword = calcTreeElement().findChildByRole(ChildRole.CLASS_OR_INTERFACE_KEYWORD);
        if (keyword.getElementType() == CLASS_KEYWORD) {
          isInterface = false;
        }
        else if (keyword.getElementType() == INTERFACE_KEYWORD) {
          isInterface = true;
        }
        else if (keyword.getElementType() == ENUM_KEYWORD) {
          isInterface = false;
        }
        else {
          LOG.assertTrue(false);
          isInterface = false;
        }
      }
      else {
        isInterface = getRepositoryManager().getClassView().isInterface(getRepositoryId());
      }
      cachedIsInterface = myCachedIsInterface = isInterface ? Boolean.TRUE : Boolean.FALSE;
    }
    return cachedIsInterface.booleanValue();
  }

  public boolean isAnnotationType() {
    Boolean cachedIsAnnotationType = myCachedIsAnnotationType;
    if (cachedIsAnnotationType == null) {
      boolean isAnnotationType = false;
      if (isInterface()) {
        if (getTreeElement() != null) {
          isAnnotationType = calcTreeElement().findChildByRole(ChildRole.AT) != null;
        }
        else {
          isAnnotationType = getRepositoryManager().getClassView().isAnnotationType(getRepositoryId());
        }
      }
      cachedIsAnnotationType = myCachedIsAnnotationType = isAnnotationType ? Boolean.TRUE : Boolean.FALSE;
    }
    return cachedIsAnnotationType.booleanValue();
  }

  public boolean isEnum() {
    Boolean cachedIsEnum = myCachedIsEnum;
    if (cachedIsEnum == null) {
      final boolean isEnum;
      if (getTreeElement() != null) {
        isEnum = ((ClassElement)getTreeElement()).isEnum();
      }
      else {
        isEnum = getRepositoryManager().getClassView().isEnum(getRepositoryId());
      }
      cachedIsEnum = myCachedIsEnum = Boolean.valueOf(isEnum);
    }
    return cachedIsEnum.booleanValue();
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitClass(this);
  }

  public String toString(){
    return "PsiClass:" + getName();
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    if (isEnum()) {
      String name = getName();
      if (name != null) {
        try {
          if (myValuesMethod == null || myValueOfMethod == null || !name.equals(myCachedForLongName)) {
            myCachedForLongName = name;
            final PsiMethod valuesMethod = getManager().getElementFactory().createMethodFromText("public static " + name + "[] values() {}", this);
            myValuesMethod = new LightMethod(getManager(), valuesMethod, this);
            final PsiMethod valueOfMethod = getManager().getElementFactory().createMethodFromText("public static " + name + " valueOf(String name) throws IllegalArgumentException {}", this);
            myValueOfMethod = new LightMethod(getManager(), valueOfMethod, this);
          }
          final NameHint nameHint = processor.getHint(NameHint.class);
          final ElementClassHint classHint = processor.getHint(ElementClassHint.class);
          if (nameHint == null || VALUES_METHOD.equals(nameHint.getName())) {
            if (classHint == null || classHint.shouldProcess(PsiMethod.class)) {
              if (!processor.execute(myValuesMethod, PsiSubstitutor.EMPTY)) return false;
            }
          }
          if (nameHint == null || VALUE_OF_METHOD.equals(nameHint.getName())) {
            if (classHint == null || classHint.shouldProcess(PsiMethod.class)) {
              if (!processor.execute(myValueOfMethod, PsiSubstitutor.EMPTY)) return false;
            }
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }

    return PsiClassImplUtil.processDeclarationsInClass(this, processor, substitutor, new HashSet<PsiClass>(), lastParent, place, false);
  }

  public PsiElement setName(@NotNull String newName) throws IncorrectOperationException{
    String oldName = getName();
    boolean isRenameFile = isRenameFileOnRenaming();

    SharedPsiElementImplUtil.setName(getNameIdentifier(), newName);

    if (isRenameFile) {
      PsiFile file = (PsiFile)getParent();
      String fileName = file.getName();
      int dotIndex = fileName.lastIndexOf('.');
      file.setName(dotIndex >= 0 ? newName + "." + fileName.substring(dotIndex + 1) : newName);
    }

    // rename constructors
    PsiMethod[] methods = getMethods();
    for (PsiMethod method : methods) {
      if (method.isConstructor() && method.getName().equals(oldName)) {
        method.setName(newName);
      }
    }

    return this;
  }

  private boolean isRenameFileOnRenaming() {
    final PsiElement parent = getParent();
    if (parent instanceof PsiFile) {
      PsiFile file = (PsiFile)parent;
      String fileName = file.getName();
      if (fileName == null) return false;
      int dotIndex = fileName.lastIndexOf('.');
      String name = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
      String oldName = getName();
      return name.equals(oldName);
    }
    else {
      return false;
    }
  }

  public PsiMetaData getMetaData(){
    return MetaRegistry.getMeta(this);
  }

  public boolean isMetaEnough(){
    return false;
  }

  // optimization to not load tree when resolving bases of anonymous and locals
  // if there is no local classes with such name in scope it's possible to use outer scope as context
  @Nullable
  protected PsiElement calcBasesResolveContext(String baseClassName, final PsiElement defaultResolveContext) {
    return calcBasesResolveContext(this, baseClassName, true, defaultResolveContext);
  }

  @Nullable
  private PsiElement calcBasesResolveContext(PsiClass aClass,
                                             String className,
                                             boolean isInitialClass,
                                             final PsiElement defaultResolveContext) {
      boolean isAnonOrLocal = false;
      if (aClass instanceof PsiAnonymousClass){
        isAnonOrLocal = true;
      }
      else {
        long scopeId = ((PsiClassImpl)aClass).getParentId();
        RepositoryElementType scopeType = myManager.getRepositoryManager().getElementType(scopeId);
        boolean isLocalClass = scopeType == RepositoryElementType.METHOD || scopeType == RepositoryElementType.FIELD || scopeType == RepositoryElementType.CLASS_INITIALIZER;
        if (isLocalClass){
          isAnonOrLocal = true;
        }
      }

      if (!isAnonOrLocal) {
        return isInitialClass ? defaultResolveContext : aClass;
      }

      if (!isInitialClass){
        if (aClass.findInnerClassByName(className, true) != null) return aClass;
      }

      long classId = ((RepositoryPsiElement)aClass).getRepositoryId();
      RepositoryManager repositoryManager = myManager.getRepositoryManager();
      long scopeId = repositoryManager.getClassView().getParent(classId);
      long[] classesInScope = repositoryManager.getItemView(scopeId).getChildren(scopeId, RepositoryElementType.CLASS);

      boolean needPreciseContext = false;
      if (classesInScope.length > 1){
        for (long id : classesInScope) {
          if (id == classId) continue;
          String className1 = repositoryManager.getClassView().getName(id);
          if (className.equals(className1)) {
            needPreciseContext = true;
            break;
          }
        }
      }
      else{
        LOG.assertTrue(classesInScope.length == 1);
        LOG.assertTrue(classesInScope[0] == classId);
      }

      if (needPreciseContext){
        return aClass.getParent();
      }
      else{
        PsiElement context = myManager.getRepositoryElementsManager().findOrCreatePsiElementById(scopeId);
        if (context instanceof PsiClass){
          return calcBasesResolveContext((PsiClass)context, className, false, defaultResolveContext);
        }
        else if (context instanceof PsiMember){
          return calcBasesResolveContext(((PsiMember)context).getContainingClass(), className, false, defaultResolveContext);
        }
        else{
          LOG.assertTrue(false);
          return context;
        }
      }
    }

  public boolean isInheritor(PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Nullable
  public PomMemberOwner getPom() {
    //TODO:
    return null;
  }

  public ItemPresentation getPresentation() {
    return ClassPresentationUtil.getPresentation(this);
  }
}

