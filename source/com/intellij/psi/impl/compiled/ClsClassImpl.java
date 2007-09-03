package com.intellij.psi.impl.compiled;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.PomMemberOwner;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.ClassView;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.cls.BytePointer;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.cls.ClsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.*;

public class ClsClassImpl extends ClsRepositoryPsiElement implements PsiClass, ClsModifierListOwner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsClassImpl");

  private ClassFileData myClassFileData; // it's null when repository id exists

  // these fields are not used when has repository id
  private volatile PsiElement myParent;
  private volatile PsiField[] myFields = null;
  private volatile PsiMethod[] myMethods = null;
  private volatile PsiMethod[] myConstructors = null;
  private volatile PsiClass[] myInnerClasses = null;

  private volatile Map<String, PsiField> myCachedFieldsMap = null;
  private volatile Map<String, PsiMethod[]> myCachedMethodsMap = null;
  private volatile Map<String, PsiClass> myCachedInnersMap = null;


  private volatile String myQualifiedName = null;
  private volatile String myName = null;
  private volatile PsiIdentifier myNameIdentifier = null;
  private volatile ClsModifierListImpl myModifierList = null;
  private volatile PsiReferenceList myExtendsList = null;
  private volatile PsiReferenceList myImplementsList = null;
  private volatile PsiTypeParameterList myTypeParameters = null;
  private volatile Boolean myIsDeprecated = null;
  private volatile PsiDocComment myDocComment = null;
  private volatile ClsAnnotationImpl[] myAnnotations = null;
  private volatile Boolean myCachedIsInterface = null;
  private volatile Boolean myCachedIsAnnotationType = null;

  public ClsClassImpl(PsiManagerImpl manager, ClsElementImpl parent, final ClassFileData classFileData) {
    super(manager, -1);
    myParent = parent;
    myClassFileData = classFileData;
  }

  public ClsClassImpl(PsiManagerImpl manager, long repositoryId) {
    super(manager, repositoryId);
    myClassFileData = null;
    myParent = null;
  }

  public void invalidate() {
    myParent = null;
    setRepositoryId(-1);
  }

  boolean isContentsLoaded() {
    return myClassFileData != null;
  }

  ClassFileData getClassFileData() {
    VirtualFile vFile = getContainingFile().getVirtualFile();
    if (vFile != null) {
      LOG.assertTrue(!myManager.isAssertOnFileLoading(vFile));
    }
    return myClassFileData;
  }

  public void setRepositoryId(long repositoryId) {
    super.setRepositoryId(repositoryId);
    if (repositoryId >= 0) {
      myClassFileData = null;
    }
  }

  public PsiElement getParent() {
    if (myParent == null) {
        long repositoryId = getRepositoryId();
        if (repositoryId >= 0) {
          long parentId = getRepositoryManager().getClassView().getParent(repositoryId);
          myParent = getRepositoryElementsManager().findOrCreatePsiElementById(parentId);
        }
      }
    return myParent;
  }

  public PsiFile getContainingFile() {
    PsiElement parent = getParent();
    if (parent == null) {
      if (!isValid()) throw new PsiInvalidElementAccessException(this);
      return null;
    }
    return parent.getContainingFile();
  }

  @NotNull
  public PsiElement[] getChildren() {
    PsiIdentifier name = getNameIdentifier();
    PsiDocComment docComment = getDocComment();
    PsiModifierList modifierList = getModifierList();
    PsiReferenceList extendsList = getExtendsList();
    PsiReferenceList implementsList = getImplementsList();
    PsiField[] fields = getFields();
    PsiMethod[] methods = getMethods();
    PsiClass[] classes = getInnerClasses();

    int count =
      (docComment != null ? 1 : 0)
      + 1 // modifierList
      + 1 // name
      + 1 // extends list
      + 1 // implementsList
      + fields.length
      + methods.length
      + classes.length;
    PsiElement[] children = new PsiElement[count];

    int offset = 0;
    if (docComment != null) {
      children[offset++] = docComment;
    }

    children[offset++] = modifierList;
    children[offset++] = name;
    children[offset++] = extendsList;
    children[offset++] = implementsList;

    System.arraycopy(fields, 0, children, offset, fields.length);
    offset += fields.length;
    System.arraycopy(methods, 0, children, offset, methods.length);
    offset += methods.length;
    System.arraycopy(classes, 0, children, offset, classes.length);
    /*offset += classes.length;*/

    return children;
  }

  @NotNull
  public PsiIdentifier getNameIdentifier() {
    if (myNameIdentifier == null) {
      String qName = getQualifiedName();
      String name = PsiNameHelper.getShortClassName(qName);
      if (name.length() == 0) {
        name = "_";
      }
      myNameIdentifier = new ClsIdentifierImpl(this, name);
    }
    return myNameIdentifier;
  }

  @NotNull
  public String getName() {
    if (myName == null) {
      String qName = getQualifiedName();
      myName = PsiNameHelper.getShortClassName(qName);
    }
    return myName;
  }

  @NotNull
  public PsiTypeParameterList getTypeParameterList() {
    if (myTypeParameters == null) {
      long repositoryId = getRepositoryId();
      PsiTypeParameterList typeParameters = null;
      if (repositoryId < 0) {
        if (!parseViaGenericSignature()) {
          typeParameters = new ClsTypeParametersListImpl(this, ClsTypeParameterImpl.EMPTY_ARRAY);
        }
      }
      else {
        ClassView classView = getRepositoryManager().getClassView();
        int count = classView.getParametersListSize(repositoryId);
        if (count == 0) {
          typeParameters = new ClsTypeParametersListImpl(this, ClsTypeParameterImpl.EMPTY_ARRAY);
        }
        else {
          StringBuilder compiledParams = new StringBuilder();
          compiledParams.append('<');
          for (int i = 0; i < count; i++) {
            compiledParams.append(classView.getParameterText(repositoryId, i));
          }
          compiledParams.append('>');
          try {
            final String signature = compiledParams.toString();
            typeParameters = GenericSignatureParsing.parseTypeParametersDeclaration(new StringCharacterIterator(signature, 0), this, signature);
          }
          catch (ClsFormatException e) {
            LOG.error(e); // dsl: this should not happen
          }
        }
      }
      setTypeParametersListIfNull(typeParameters);
    }
    return myTypeParameters;
  }

  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    SharedPsiElementImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @NotNull
  public String getQualifiedName() {
    if (myQualifiedName == null) {
      long repositoryId = getRepositoryId();
      if (repositoryId < 0) {
        try {
          ClassFileData classFileData = getClassFileData();
          BytePointer ptr = new BytePointer(classFileData.getData(), classFileData.getConstantPoolEnd() + 2);
          ptr.offset = classFileData.getOffsetInConstantPool(ClsUtil.readU2(ptr));
          int tag = ClsUtil.readU1(ptr);
          if (tag != ClsUtil.CONSTANT_Class) {
            throw new ClsFormatException();
          }
          ptr.offset = classFileData.getOffsetInConstantPool(ClsUtil.readU2(ptr));
          String className = ClsUtil.readUtf8Info(ptr, '/', '.');
          myQualifiedName = ClsUtil.convertClassName(className, false);
        }
        catch (ClsFormatException e) {
          myQualifiedName = "";
        }
      }
      else {
        String qualifiedName = getRepositoryManager().getClassView().getQualifiedName(repositoryId);
        if (qualifiedName == null) {
          qualifiedName = "";
        }
        myQualifiedName = qualifiedName;
      }
    }
    return myQualifiedName;
  }

  @NotNull
  public PsiModifierList getModifierList() {
    if (myModifierList == null) {
      int flags = getAccessFlags();
      myModifierList = new ClsModifierListImpl(this, flags);
    }
    return myModifierList;
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  public PsiReferenceList getExtendsList() {
    if (myExtendsList == null) {
      long repositoryId = getRepositoryId();
      PsiReferenceList extendsList;
      if (repositoryId < 0) {
        try {
          if (parseViaGenericSignature()) return myExtendsList;
          if (!isInterface()) {
            extendsList = buildSuperList(PsiKeyword.EXTENDS);
          }
          else {
            extendsList = buildInterfaceList(PsiKeyword.EXTENDS);
          }
        }
        catch (ClsFormatException e) {
          extendsList = new ClsReferenceListImpl(this, PsiJavaCodeReferenceElement.EMPTY_ARRAY, PsiKeyword.EXTENDS);
        }
      }
      else {
        ClassView classView = getRepositoryManager().getClassView();
        String[] refTexts = classView.getExtendsList(repositoryId);
        ClsJavaCodeReferenceElementImpl[] refs = refTexts.length == 0 ? ClsJavaCodeReferenceElementImpl.EMPTY_ARRAY : new ClsJavaCodeReferenceElementImpl[refTexts.length];
        for (int i = 0; i < refTexts.length; i++) {
          refs[i] = new ClsJavaCodeReferenceElementImpl(null, refTexts[i]);
        }
        extendsList = new ClsReferenceListImpl(this, refs, PsiKeyword.EXTENDS);
        for (ClsJavaCodeReferenceElementImpl ref : refs) {
          ref.setParent(extendsList);
        }
      }
      setExtendsListIfNull(extendsList);
    }
    return myExtendsList;
  }

  private synchronized void setExtendsListIfNull(PsiReferenceList list) {
    if (myExtendsList == null) {
      myExtendsList = list;
    }
  }
  private synchronized void setImplementsListIfNull(PsiReferenceList list) {
    if (myImplementsList == null) {
      myImplementsList = list;
    }
  }
  private synchronized void setTypeParametersListIfNull(PsiTypeParameterList list) {
    if (myTypeParameters == null) {
      myTypeParameters = list;
    }
  }
  private boolean parseViaGenericSignature() {
    try {
      String signature = getSignatureAttribute();
      if (signature == null) return false;

      CharacterIterator iterator = new StringCharacterIterator(signature, 0);
      setTypeParametersListIfNull(GenericSignatureParsing.parseTypeParametersDeclaration(iterator, this, signature));

      PsiJavaCodeReferenceElement[] supers = GenericSignatureParsing.parseToplevelClassRefSignatures(iterator, this);

      PsiReferenceList implementsList;
      PsiReferenceList extendsList;
      if (!isInterface()) {
        if (supers.length > 0 && !supers[0].getCanonicalText().equals("java.lang.Object")) {
          extendsList = new ClsReferenceListImpl(this, new PsiJavaCodeReferenceElement[]{supers[0]}, PsiKeyword.EXTENDS);
        }
        else {
          extendsList = new ClsReferenceListImpl(this, PsiJavaCodeReferenceElement.EMPTY_ARRAY, PsiKeyword.EXTENDS);
        }

        PsiJavaCodeReferenceElement[] interfaces = buildInterfaces(supers);
        implementsList = new ClsReferenceListImpl(this, interfaces, PsiKeyword.IMPLEMENTS);
      }
      else {
        implementsList = new ClsReferenceListImpl(this, PsiJavaCodeReferenceElement.EMPTY_ARRAY, PsiKeyword.IMPLEMENTS);
        if (supers.length == 0 || supers[0].getCanonicalText().equals("java.lang.Object")) {
          supers = buildInterfaces(supers);
        }
        extendsList = new ClsReferenceListImpl(this, supers, PsiKeyword.EXTENDS);
      }
      setExtendsListIfNull(extendsList);
      setImplementsListIfNull(implementsList);
    }
    catch (ClsFormatException e) {
      return false;
    }

    return true;
  }

  private static PsiJavaCodeReferenceElement[] buildInterfaces(PsiJavaCodeReferenceElement[] supers) {
    PsiJavaCodeReferenceElement[] interfaces;
    if (supers.length > 0) {
      interfaces = new PsiJavaCodeReferenceElement[supers.length - 1];
      System.arraycopy(supers, 1, interfaces, 0, supers.length - 1);
    }
    else {
      interfaces = PsiJavaCodeReferenceElement.EMPTY_ARRAY;
    }
    return interfaces;
  }

  @NotNull
  public PsiReferenceList getImplementsList() {
    if (myImplementsList == null) {
      PsiReferenceList implementsList;
      if (!isInterface()) {
        long repositoryId = getRepositoryId();
        if (repositoryId < 0) {
          try {
            if (parseViaGenericSignature()) return myImplementsList;
            implementsList = buildInterfaceList(PsiKeyword.IMPLEMENTS);
          }
          catch (ClsFormatException e) {
            implementsList = new ClsReferenceListImpl(this, PsiJavaCodeReferenceElement.EMPTY_ARRAY, PsiKeyword.IMPLEMENTS);
          }
        }
        else {
          ClassView classView = getRepositoryManager().getClassView();
          String[] refTexts = classView.getImplementsList(repositoryId);
          ClsJavaCodeReferenceElementImpl[] refs = refTexts.length == 0 ? ClsJavaCodeReferenceElementImpl.EMPTY_ARRAY : new ClsJavaCodeReferenceElementImpl[refTexts.length];
          for (int i = 0; i < refTexts.length; i++) {
            refs[i] = new ClsJavaCodeReferenceElementImpl(null, refTexts[i]);
          }
          implementsList = new ClsReferenceListImpl(this, refs, PsiKeyword.IMPLEMENTS);
          for (ClsJavaCodeReferenceElementImpl ref : refs) {
            ref.setParent(implementsList);
          }
        }
      }
      else {
        implementsList = new ClsReferenceListImpl(this, PsiJavaCodeReferenceElement.EMPTY_ARRAY, PsiKeyword.IMPLEMENTS);
      }
      setImplementsListIfNull(implementsList);
    }
    return myImplementsList;
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

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : null;
  }

  @NotNull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return PsiSuperMethodImplUtil.getVisibleSignatures(this);
  }

  private PsiReferenceList buildSuperList(String type) throws ClsFormatException {
    ClassFileData classFileData = getClassFileData();
    int offset = classFileData.getConstantPoolEnd() + 4;
    if (offset + 2 > myClassFileData.data.length) {
      throw new ClsFormatException();
    }
    int b1 = myClassFileData.data[offset++] & 0xFF;
    int b2 = myClassFileData.data[offset/*++*/] & 0xFF;
    int index = (b1 << 8) + b2;
    ClsJavaCodeReferenceElementImpl ref = index != 0 ? classFileData.buildReference(index) : null;
    if (ref != null && "java.lang.Object".equals(ref.getCanonicalText())) {
      ref = null;
    }
    PsiReferenceList list = new ClsReferenceListImpl(this,
                                                     ref != null
                                                     ? new PsiJavaCodeReferenceElement[]{ref}
                                                     : PsiJavaCodeReferenceElement.EMPTY_ARRAY,
                                                     type);
    if (ref != null) {
      ref.setParent(list);
    }
    return list;
  }

  private PsiReferenceList buildInterfaceList(String type) throws ClsFormatException {
    ClassFileData classFileData = getClassFileData();
    int offset = classFileData.getConstantPoolEnd() + 6;
    byte[] data = classFileData.getData();
    if (offset + 2 > data.length) {
      throw new ClsFormatException();
    }
    int b1 = data[offset++] & 0xFF;
    int b2 = data[offset++] & 0xFF;
    int count = (b1 << 8) + b2;
    if (offset + count * 2 > data.length) {
      throw new ClsFormatException();
    }
    ClsJavaCodeReferenceElementImpl[] refs = count == 0 ? ClsJavaCodeReferenceElementImpl.EMPTY_ARRAY : new ClsJavaCodeReferenceElementImpl[count];
    for (int i = 0; i < count; i++) {
      b1 = data[offset++] & 0xFF;
      b2 = data[offset++] & 0xFF;
      int index = (b1 << 8) + b2;
      refs[i] = classFileData.buildReference(index);
    }
    PsiReferenceList list = new ClsReferenceListImpl(this, refs, type);
    for (int i = 0; i < count; i++) {
      refs[i].setParent(list);
    }
    return list;
  }

  @NotNull
  public PsiField[] getFields() {
    PsiField[] fields = myFields;
    if (fields == null) {
      long repositoryId = getRepositoryId();
      if (repositoryId < 0) {
        try {
          ClassFileData classFileData = getClassFileData();
          BytePointer ptr = new BytePointer(classFileData.getData(), classFileData.getConstantPoolEnd() + 6);
          int count = ClsUtil.readU2(ptr);
          ptr.offset += count * 2; // skip interfaces
          count = ClsUtil.readU2(ptr);
          ArrayList<PsiField> array = new ArrayList<PsiField>();
          for (int i = 0; i < count; i++) {
            PsiField field;
            if (isEnumField(ptr.offset)) {
              field = new ClsEnumConstantImpl(this, ptr.offset);
            }
            else {
              field = new ClsFieldImpl(this, ptr.offset);
            }
            String name = field.getName();
            //if (name.indexOf('$') < 0 && name.indexOf('<') < 0){ // skip synthetic fields
            if (myManager.getNameHelper().isIdentifier(name) && name.indexOf('$') < 0) { // skip synthetic&obfuscated fields
              array.add(field);
            }
            ptr.offset += 6;
            ClsUtil.skipAttributes(ptr);
          }
          fields = array.isEmpty() ? PsiField.EMPTY_ARRAY : array.toArray(new PsiField[array.size()]);
        }
        catch (ClsFormatException e) {
          fields = PsiField.EMPTY_ARRAY;
        }
      }
      else {
        long[] fieldIds = getRepositoryManager().getClassView().getFields(repositoryId);
        fields = fieldIds.length == 0 ? PsiField.EMPTY_ARRAY : new PsiField[fieldIds.length];
        RepositoryElementsManager repositoryElementsManager = getRepositoryElementsManager();
        for (int i = 0; i < fieldIds.length; i++) {
          long id = fieldIds[i];
          fields[i] = (PsiField)repositoryElementsManager.findOrCreatePsiElementById(id);
        }
      }
      myFields = fields;
    }
    return fields;
  }

  private boolean isEnumField(int ptrOffset) {
    int flags = 0;
    try {
      int offset = ptrOffset;
      byte[] data = getClassFileData().getData();
      if (offset + 2 > data.length) {
        throw new ClsFormatException();
      }
      int b1 = data[offset++] & 0xFF;
      int b2 = data[offset] & 0xFF;
      flags = (b1 << 8) + b2;
    }
    catch (ClsFormatException e) {
    }

    return (flags & ClsUtil.ACC_ENUM) != 0;
  }

  @NotNull
  public PsiMethod[] getMethods() {
    PsiMethod[] methods = myMethods;
    if (methods == null) {
      long repositoryId = getRepositoryId();

      if (repositoryId < 0) {
        try {
          ClassFileData classFileData = getClassFileData();
          BytePointer ptr = new BytePointer(classFileData.getData(), classFileData.getConstantPoolEnd() + 6);
          int count = ClsUtil.readU2(ptr);
          ptr.offset += count * 2; // skip interfaces
          count = ClsUtil.readU2(ptr);
          for (int i = 0; i < count; i++) { // skip fields
            ptr.offset += 6;
            ClsUtil.skipAttributes(ptr);
          }
          count = ClsUtil.readU2(ptr);
          ArrayList<PsiMethod> array = new ArrayList<PsiMethod>();
          for (int i = 0; i < count; i++) {
            ClsMethodImpl method = new ClsMethodImpl(this, ptr.offset);
            String name = method.getName();
            if (!method.isBridge() && !method.isSynthetic()) { //skip bridge & synthetic methods
              if (myManager.getNameHelper().isIdentifier(name)) {
                array.add(method);
              }
            }
            ptr.offset += 6;
            ClsUtil.skipAttributes(ptr);
          }
          methods = array.toArray(new PsiMethod[array.size()]);
        }
        catch (ClsFormatException e) {
          methods = PsiMethod.EMPTY_ARRAY;
        }
      }
      else {
        long[] methodIds = getRepositoryManager().getClassView().getMethods(repositoryId);
        methods = methodIds.length == 0 ? PsiMethod.EMPTY_ARRAY : new PsiMethod[methodIds.length];
        RepositoryElementsManager repositoryElementsManager = getRepositoryElementsManager();
        for (int i = 0; i < methodIds.length; i++) {
          long id = methodIds[i];
          methods[i] = (PsiMethod)repositoryElementsManager.findOrCreatePsiElementById(id);
        }
      }
      myMethods = methods;
    }
    return methods;
  }

  @NotNull
  public PsiMethod[] getConstructors() {
    if (myConstructors == null) {
      myConstructors = PsiImplUtil.getConstructors(this);
    }
    return myConstructors;
  }

  @NotNull
  public PsiClass[] getInnerClasses() {
    if (myInnerClasses == null) {
      long repositoryId = getRepositoryId();
      if (repositoryId < 0) {
        VirtualFile vFile = myClassFileData.vFile;
        VirtualFile parentFile = vFile.getParent();
        if (parentFile == null) return PsiClass.EMPTY_ARRAY;
        String name = vFile.getNameWithoutExtension();
        String prefix = name + "$";
        ArrayList<PsiClass> array = new ArrayList<PsiClass>();
        VirtualFile[] children = parentFile.getChildren();
        for (VirtualFile child : children) {
          String childName = child.getNameWithoutExtension();
          if (childName.startsWith(prefix)) {
            String innerName = childName.substring(prefix.length());
            if (innerName.indexOf('$') >= 0) continue;
            if (!myManager.getNameHelper().isIdentifier(innerName)) continue;
            PsiClass aClass = new ClsClassImpl(myManager, this, new ClassFileData(child));
            array.add(aClass);
          }
        }
        myInnerClasses = array.toArray(new PsiClass[array.size()]);
      }
      else {
        long[] classIds = getRepositoryManager().getClassView().getInnerClasses(repositoryId);
        PsiClass[] classes = new PsiClass[classIds.length];
        RepositoryElementsManager repositoryElementsManager = getRepositoryElementsManager();
        for (int i = 0; i < classIds.length; i++) {
          long id = classIds[i];
          classes[i] = (PsiClass)repositoryElementsManager.findOrCreatePsiElementById(id);
        }
        myInnerClasses = classes;
      }
    }
    return myInnerClasses;
  }

  @NotNull
  public PsiClassInitializer[] getInitializers() {
    return PsiClassInitializer.EMPTY_ARRAY;
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
    if (!checkBases) {
      if (myCachedFieldsMap == null) {
        HashMap<String, PsiField> cachedFieldsMap = new HashMap<String, PsiField>();
        final PsiField[] fields = getFields();
        for (final PsiField field : fields) {
          cachedFieldsMap.put(field.getName(), field);
        }
        myCachedFieldsMap = cachedFieldsMap;
      }
      return myCachedFieldsMap.get(name);
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
    if (!checkBases) {
      if (myCachedMethodsMap == null) {
        Map<String, PsiMethod[]> methodsMap = new HashMap<String, PsiMethod[]>();
        Map<String, List<PsiMethod>> cachedMethodsMap = new HashMap<String, List<PsiMethod>>();
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
          methodsMap.put(methodName, cachedMethodsMap.get(methodName).toArray(PsiMethod.EMPTY_ARRAY));
        }
        myCachedMethodsMap = methodsMap;
      }
      final PsiMethod[] psiMethods = myCachedMethodsMap.get(name);
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
    if (!checkBases) {
      if (myCachedInnersMap == null) {
        HashMap<String, PsiClass> cachedInnersMap = new HashMap<String, PsiClass>();
        final PsiClass[] classes = getInnerClasses();
        for (final PsiClass psiClass : classes) {
          cachedInnersMap.put(psiClass.getName(), psiClass);
        }
        myCachedInnersMap = cachedInnersMap;
      }
      return myCachedInnersMap.get(name);
    }
    return PsiClassImplUtil.findInnerByName(this, name, checkBases);
  }

  public boolean isDeprecated() {
    if (myIsDeprecated == null) {
      long repositoryId = getRepositoryId();
      if (repositoryId < 0) {
        try {
          boolean isDeprecated = readClassAttribute("Deprecated") != null;
          myIsDeprecated = isDeprecated ? Boolean.TRUE : Boolean.FALSE;
        }
        catch (ClsFormatException e) {
          myIsDeprecated = Boolean.FALSE;
        }
      }
      else {
        boolean isDeprecated = getRepositoryManager().getClassView().isDeprecated(repositoryId);
        myIsDeprecated = isDeprecated ? Boolean.TRUE : Boolean.FALSE;
      }
    }
    return myIsDeprecated.booleanValue();
  }

  public String getSourceFileName() {
    long repositoryId = getRepositoryId();
    if (repositoryId < 0) {
      try {
        String sourceFileName = getClassFileData().readUtf8Attribute(readClassAttribute("SourceFile"));
        if (sourceFileName == null) {
          sourceFileName = obtainSourceFileNameFromClassFileName();
          return sourceFileName;
        }
        int slashIndex = sourceFileName.lastIndexOf('/');      // We need short name while some compilers do generate fulls
        if (slashIndex >= 0) {
          sourceFileName = sourceFileName.substring(slashIndex + 1, sourceFileName.length());
        }
        return sourceFileName;
      }
      catch (ClsFormatException e) {
        return null;
      }
    }
    else {
      ClsFileImpl file = (ClsFileImpl)getContainingFile();
      String sourceFileName = getRepositoryManager().getFileView().getSourceFileName(file.getRepositoryId());
      if (sourceFileName == null || sourceFileName.length() == 0) {
        sourceFileName = obtainSourceFileNameFromClassFileName();
      }
      return sourceFileName;
    }
  }

  @NonNls
  private String obtainSourceFileNameFromClassFileName() {
    final String name = getContainingFile().getName();
    int i = name.indexOf('$');
    if (i < 0) {
      i = name.indexOf('.');
      if (i < 0) {
        i = name.length();
      }
    }
    return name.substring(0, i) + ".java";
  }

  private String getSignatureAttribute() throws ClsFormatException {
    return getClassFileData().readUtf8Attribute(readClassAttribute("Signature"));
  }

  private BytePointer readClassAttribute(@NonNls String attributeName) throws ClsFormatException {
    ClassFileData classFileData = getClassFileData();
    return getClassFileData().findAttribute(classFileData.getOffsetOfAttributesSection(), attributeName);
  }

  public PsiDocComment getDocComment() {
    if (!isDeprecated()) return null;

    if (myDocComment == null) {
      myDocComment = new ClsDocCommentImpl(this);
    }
    return myDocComment;
  }

  public PsiJavaToken getLBrace() {
    return null;
  }

  public PsiJavaToken getRBrace() {
    return null;
  }

  public boolean isInterface() {
    Boolean isInterface = myCachedIsInterface;
    if (isInterface == null) {
      long repositoryId = getRepositoryId();
      if (repositoryId < 0) {
        isInterface = (getAccessFlags() & ClsUtil.ACC_INTERFACE) != 0;
      }
      else {
        isInterface = getRepositoryManager().getClassView().isInterface(repositoryId);
      }
      myCachedIsInterface = isInterface;
    }
    return isInterface;
  }

  public boolean isAnnotationType() {
    Boolean isAnnotationType = myCachedIsAnnotationType;
    if (isAnnotationType == null) {
      long repositoryId = getRepositoryId();
      if (repositoryId < 0) {
        isAnnotationType = (getAccessFlags() & ClsUtil.ACC_ANNOTATION) != 0;
      }
      else {
        isAnnotationType = getRepositoryManager().getClassView().isAnnotationType(repositoryId);
      }
      myCachedIsAnnotationType = isAnnotationType;
    }
    return isAnnotationType;
  }

  public boolean isEnum() {
    PsiField[] fields = getFields();
    return fields.length != 0 && fields[0] instanceof ClsEnumConstantImpl;
  }

  private int getAccessFlags() {
    long repositoryId = getRepositoryId();
    if (repositoryId < 0) {
      try {
        ClassFileData classFileData = getClassFileData();
        int offset = classFileData.getConstantPoolEnd();
        byte[] data = classFileData.getData();
        if (offset + 2 > data.length) {
          throw new ClsFormatException();
        }
        int b1 = data[offset++] & 0xFF;
        int b2 = data[offset/*++*/] & 0xFF;
        int flags = ((b1 << 8) + b2) & ClsUtil.ACC_CLASS_MASK;

        PsiElement parent = getParent();
        if (parent instanceof PsiClass) {
          PsiClass aClass = (PsiClass)parent;
          if (aClass.isInterface()) {
            flags |= ClsUtil.ACC_STATIC;
          }
          else {
            flags &= ~ClsUtil.ACC_STATIC;

            BytePointer ptr = readClassAttribute("InnerClasses");
            if (ptr != null) {
              //Skip attribute_length
              ptr.offset += 4;
              int numClasses = ClsUtil.readU2(ptr);
              int startOffset = ptr.offset + 4;
              for (int i = 0; i < numClasses; i++) {
                BytePointer ptr1 = new BytePointer(classFileData.getData(), startOffset + i * 8);
                int innerNameIdx = ClsUtil.readU2(ptr1);
                if (innerNameIdx == 0) {
                  continue;
                }
                int innerNameOffset = classFileData.getOffsetInConstantPool(innerNameIdx);
                String innerName = ClsUtil.convertClassName(ClsUtil.readUtf8Info(classFileData.getData(), innerNameOffset), true);
                if (getName().equals(innerName)) {
                  flags = ClsUtil.readU2(ptr1);
                  break;
                }
              }
            }
          }
        }
        return flags;
      }
      catch (ClsFormatException e) {
        return 0;
      }
    }
    else {
      ClassView classView = getRepositoryManager().getClassView();
      return classView.getModifiers(repositoryId);
    }
  }

  public void appendMirrorText(final int indentLevel, @NonNls final StringBuffer buffer) {
    ClsDocCommentImpl docComment = (ClsDocCommentImpl)getDocComment();
    if (docComment != null) {
      docComment.appendMirrorText(indentLevel, buffer);
      goNextLine(indentLevel, buffer);
    }
    ((ClsElementImpl)getModifierList()).appendMirrorText(indentLevel, buffer);
    buffer.append(isEnum() ? "enum " : isAnnotationType() ? "@interface " : isInterface() ? "interface " : "class ");
    ((ClsElementImpl)getNameIdentifier()).appendMirrorText(indentLevel, buffer);
    ((ClsTypeParametersListImpl)getTypeParameterList()).appendMirrorText(indentLevel, buffer);
    buffer.append(' ');
    if (!isEnum() && !isAnnotationType()) {
      ((ClsElementImpl)getExtendsList()).appendMirrorText(indentLevel, buffer);
      buffer.append(' ');
    }
    if (!isInterface()) {
      ((ClsElementImpl)getImplementsList()).appendMirrorText(indentLevel, buffer);
    }
    buffer.append('{');
    final int newIndentLevel = indentLevel + getIndentSize();
    PsiField[] fields = getFields();
    if (fields.length > 0) {
      goNextLine(newIndentLevel, buffer);
      for (int i = 0; i < fields.length; i++) {
        PsiField field = fields[i];
        ((ClsElementImpl)field).appendMirrorText(newIndentLevel, buffer);
        if (field instanceof ClsEnumConstantImpl) {
          if (i < fields.length - 1 && fields[i + 1] instanceof ClsEnumConstantImpl) {
            buffer.append(", ");
          }
          else {
            buffer.append(";");
            if (i < fields.length - 1) {
              goNextLine(newIndentLevel, buffer);
            }
          }
        } else if (i < fields.length - 1) {
          goNextLine(newIndentLevel, buffer);
        }
      }
    }

    PsiMethod[] methods = getMethods();
    if (methods.length > 0) {
      goNextLine(newIndentLevel, buffer);
      goNextLine(newIndentLevel, buffer);
      for (int i = 0; i < methods.length; i++) {
        PsiMethod method = methods[i];
        ((ClsElementImpl)method).appendMirrorText(newIndentLevel, buffer);
        if (i < methods.length - 1) {
          goNextLine(newIndentLevel, buffer);
          goNextLine(newIndentLevel, buffer);
        }
      }
    }

    PsiClass[] classes = getInnerClasses();
    if (classes.length > 0) {
      goNextLine(newIndentLevel, buffer);
      for (int i = 0; i < classes.length; i++) {
        PsiClass aClass = classes[i];
        ((ClsElementImpl)aClass).appendMirrorText(newIndentLevel, buffer);
        if (i < classes.length - 1) {
          goNextLine(newIndentLevel, buffer);
          goNextLine(newIndentLevel, buffer);
        }
      }
    }
    goNextLine(indentLevel, buffer);
    buffer.append('}');
  }

  public void setMirror(@NotNull TreeElement element) {
    LOG.assertTrue(isValid());
    LOG.assertTrue(myMirror == null);
    myMirror = element;
    PsiClass mirror = (PsiClass)SourceTreeToPsiMap.treeElementToPsi(element);

    final PsiDocComment docComment = getDocComment();
    if (docComment != null) {
        ((ClsElementImpl)docComment).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getDocComment()));
    }
      ((ClsElementImpl)getModifierList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getModifierList()));
      ((ClsElementImpl)getNameIdentifier()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getNameIdentifier()));
    if (!isAnnotationType() && !isEnum()) {
        ((ClsElementImpl)getExtendsList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getExtendsList()));
    }
      ((ClsElementImpl)getImplementsList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getImplementsList()));
      ((ClsElementImpl)getTypeParameterList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getTypeParameterList()));

    PsiField[] fields = getFields();
    PsiField[] mirrorFields = mirror.getFields();
    if (LOG.assertTrue(fields.length == mirrorFields.length)) {
      for (int i = 0; i < fields.length; i++) {
          ((ClsElementImpl)fields[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirrorFields[i]));
      }
    }

    PsiMethod[] methods = getMethods();
    PsiMethod[] mirrorMethods = mirror.getMethods();
    if (LOG.assertTrue(methods.length == mirrorMethods.length)) {
      for (int i = 0; i < methods.length; i++) {
          ((ClsElementImpl)methods[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirrorMethods[i]));
      }
    }

    PsiClass[] classes = getInnerClasses();
    PsiClass[] mirrorClasses = mirror.getInnerClasses();
    if (LOG.assertTrue(classes.length == mirrorClasses.length)) {
      for (int i = 0; i < classes.length; i++) {
          ((ClsElementImpl)classes[i]).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirrorClasses[i]));
      }
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitClass(this);
  }

  @NonNls
  public String toString() {
    return "PsiClass:" + getName();
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull PsiSubstitutor substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return PsiClassImplUtil.processDeclarationsInClass(this, processor, substitutor, new HashSet<PsiClass>(), lastParent, place, false);
  }

  public PsiElement getScope() {
    return getParent();
  }

  public boolean isInheritorDeep(PsiClass baseClass, PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  public boolean isInheritor(@NotNull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  public PomMemberOwner getPom() {
    //TODO:
    return null;
  }

  public PsiClass getSourceMirrorClass() {
    PsiElement parent = getParent();
    final String name = getName();
    if (parent instanceof PsiFile) {
      PsiClassOwner fileNavigationElement = (PsiClassOwner)parent.getNavigationElement();
      for (PsiClass aClass : fileNavigationElement.getClasses()) {
        if (name.equals(aClass.getName())) return aClass;
      }
    }
    else {
      ClsClassImpl parentClass = (ClsClassImpl)parent;
      PsiClass parentSourceMirror = parentClass.getSourceMirrorClass();
      if (parentSourceMirror == null) return null;
      PsiClass[] innerClasses = parentSourceMirror.getInnerClasses();
      for (PsiClass innerClass : innerClasses) {
        if (name.equals(innerClass.getName())) return innerClass;
      }
    }

    return null;
  }

  public PsiElement getNavigationElement() {
    PsiClass aClass = getSourceMirrorClass();
    return aClass != null ? aClass : this;
  }

  public PsiMetaData getMetaData() {
    return MetaRegistry.getMeta(this);
  }

  public boolean isMetaEnough() {
    return false;
  }

  @NotNull
  public ClsAnnotationImpl[] getAnnotations() {
    if (myAnnotations == null) {
      ClsAnnotationsUtil.AttributeReader reader = new ClsAnnotationsUtil.AttributeReader() {
        public BytePointer readAttribute(String attributeName) {
          try {
            return readClassAttribute(attributeName);
          }
          catch (ClsFormatException e) {
            return null;
          }
        }

        public ClassFileData getClassFileData() {
          return ClsClassImpl.this.getClassFileData();
        }
      };
      myAnnotations = ClsAnnotationsUtil.getAnnotationsImpl(this, reader, myModifierList);
    }

    return myAnnotations;
  }

  public ItemPresentation getPresentation() {
    return ClassPresentationUtil.getPresentation(this);
  }
}
