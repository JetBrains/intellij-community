package com.intellij.compiler.make;

import com.intellij.compiler.SymbolTable;
import com.intellij.compiler.classParsing.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.ArrayUtil;
import gnu.trove.*;

import java.io.*;

import org.jetbrains.annotations.NonNls;

/**
 * @author Eugene Zhuravlev
 * Date: Aug 8, 2003
 * Time: 7:03:56 PM
 */
public class Cache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.Cache");

  private final ViewPool myViewPool;
  private TIntIntHashMap myQNameToClassInfoIdMap;
  private TIntIntHashMap myQNameToClassDeclarationIdMap;
  public static final int UNKNOWN = -1;

  private final File myDeclarationsIndexFile;
  private final File myClassInfosIndexFile;
  @NonNls private static final String DECLARATIONS_INDEX_FILE_NAME = "declarations_index.dat";
  @NonNls private static final String CLASSINFO_INDEX_FILE_NAME = "classinfo_index.dat";

  public Cache(String storePath, int minViewCount, int maxViewCount) throws CacheCorruptedException {
    myViewPool = new ViewPool(storePath, minViewCount, maxViewCount);
    myDeclarationsIndexFile = new File(storePath + "/" + DECLARATIONS_INDEX_FILE_NAME);
    myClassInfosIndexFile = new File(storePath + "/" + CLASSINFO_INDEX_FILE_NAME);
  }

  public synchronized void dispose() {
    myViewPool.dispose(true);
    try {
      if (myQNameToClassDeclarationIdMap != null) {
        writeIndexMap(myQNameToClassDeclarationIdMap, myDeclarationsIndexFile);
        myQNameToClassDeclarationIdMap = null;
      }
      if (myQNameToClassInfoIdMap != null) {
        writeIndexMap(myQNameToClassInfoIdMap, myClassInfosIndexFile);
        myQNameToClassInfoIdMap = null;
      }
    }
    catch (IOException e) {
      myDeclarationsIndexFile.delete();
      myClassInfosIndexFile.delete();
      LOG.info(e);
    }
  }

  public synchronized int[] getAllClassNames() throws CacheCorruptedException {
    try {
      return getQNameToClassInfoIdMap().keys();
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int importClassInfo(ClassFileReader reader, SymbolTable symbolTable) throws ClsFormatException, CacheCorruptedException {
    final int qName = symbolTable.getId(reader.getQualifiedName());

    try {
      final ClassInfoView classInfoView = myViewPool.getClassInfoView(getClassId(qName));
      final int id = classInfoView.getRecordId();

      classInfoView.setQualifiedName(qName);

      final String signature = reader.getGenericSignature();
      final int genericSignature = signature != null? symbolTable.getId(signature) : -1;
      classInfoView.setGenericSignature(genericSignature);

      classInfoView.setPath(reader.getPath());

      final String superClass = reader.getSuperClass();
      final int superQName = "".equals(superClass)? Cache.UNKNOWN : symbolTable.getId(superClass);

      LOG.assertTrue(superQName != qName);

      classInfoView.setSuperQualifiedName(superQName);

      final String[] superInterfaces = reader.getSuperInterfaces();
      final int[] interfaceNames = new int[superInterfaces.length];
      for (int idx = 0; idx < superInterfaces.length; idx++) {
        interfaceNames[idx] = symbolTable.getId(superInterfaces[idx]);
      }
      classInfoView.setSuperInterfaces(interfaceNames);

      final String sourceFileName = reader.getSourceFileName();
      if (sourceFileName != null) {
        classInfoView.setSourceFileName(sourceFileName);
      }

      classInfoView.setFlags(reader.getAccessFlags());

      classInfoView.setRuntimeVisibleAnnotations(reader.getRuntimeVisibleAnnotations());

      classInfoView.setRuntimeInvisibleAnnotations(reader.getRuntimeInvisibleAnnotations());

      classInfoView.setReferences(reader.getReferences());

      final FieldInfo[] fields = reader.getFields();
      final MethodInfo[] methods = reader.getMethods();
      MemberInfo[] members = ArrayUtil.mergeArrays(fields, methods, MemberInfo.class);
      updateMemberDeclarations(qName, members);

      registerClassId(qName, id);
    }
    catch (ClsFormatException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
    return qName;
  }

  public synchronized void importClassInfo(Cache fromCache, final int qName) throws CacheCorruptedException {
    try {
      final int fromClassId = fromCache.getClassId(qName);

      LOG.assertTrue(fromClassId != UNKNOWN);

      final ClassInfoView view = myViewPool.getClassInfoView(getClassId(qName));
      final int id = view.getRecordId();

      final ClassInfoView classInfoView = myViewPool.getClassInfoView(id);
      classInfoView.setQualifiedName(qName);
      classInfoView.setGenericSignature(fromCache.getGenericSignature(fromClassId));
      classInfoView.setPath(fromCache.getPath(fromClassId));

      final int superQualifiedName = fromCache.getSuperQualifiedName(fromClassId);
      LOG.assertTrue(qName != superQualifiedName);
      classInfoView.setSuperQualifiedName(superQualifiedName);

      classInfoView.setSuperInterfaces(fromCache.getSuperInterfaces(fromClassId));

      classInfoView.setSourceFileName(fromCache.getSourceFileName(fromClassId));

      classInfoView.setFlags(fromCache.getFlags(fromClassId));

      classInfoView.setRuntimeVisibleAnnotations(fromCache.getRuntimeVisibleAnnotations(fromClassId));

      classInfoView.setRuntimeInvisibleAnnotations(fromCache.getRuntimeInvisibleAnnotations(fromClassId));

      final int fromClassDeclarationId = fromCache.getClassDeclarationId(qName);
      final int[] fromFieldIds = fromCache.getFieldIds(fromClassDeclarationId);
      final int[] fromMethodIds = fromCache.getMethodIds(fromClassDeclarationId);
      final MemberInfo[] members = new MemberInfo[fromFieldIds.length + fromMethodIds.length];
      int currentMemberIndex = 0;
      for (int idx = 0; idx < fromFieldIds.length; idx++) {
        members[currentMemberIndex++] = fromCache.createFieldInfo(fromFieldIds[idx]);
      }
      for (int idx = 0; idx < fromMethodIds.length; idx++) {
        final int methodId = fromMethodIds[idx];
        members[currentMemberIndex++] = fromCache.createMethodInfo(methodId);
      }
      updateMemberDeclarations(qName, members);

      registerClassId(qName, id);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized AnnotationConstantValue[] getRuntimeVisibleAnnotations(int classId) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      return view.getRuntimeVisibleAnnotations();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized AnnotationConstantValue[] getRuntimeInvisibleAnnotations(int classId) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      return view.getRuntimeInvisibleAnnotations();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getClassId(int qName) throws CacheCorruptedException {
    try {
      final TIntIntHashMap classInfoMap = getQNameToClassInfoIdMap();
      if (classInfoMap.containsKey(qName)) {
        return classInfoMap.get(qName);
      }
      return UNKNOWN;
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  private synchronized void registerClassId(final int qName, final int id) throws CacheCorruptedException {
    try {
      final TIntIntHashMap classInfoMap = getQNameToClassInfoIdMap();
      classInfoMap.put(qName, id);
    }
    catch (IOException e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getClassDeclarationId(int qName) throws CacheCorruptedException {
    try {
      final TIntIntHashMap declarationsMap = getQNameToClassDeclarationIdMap();
      if (declarationsMap.containsKey(qName)) {
        return declarationsMap.get(qName);
      }
      final ClassDeclarationView view = myViewPool.getClassDeclarationView(UNKNOWN);
      view.setClassName(qName);
      final int id = view.getRecordId();
      declarationsMap.put(qName, id);
      return id;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int[] getSubclasses(int classId) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      return view.getSubclasses();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void addSubclass(int classId, int subclassQName) throws CacheCorruptedException {
    try {
      final ClassInfoView reader = myViewPool.getClassInfoView(classId);
      reader.addSubclass(subclassQName);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void removeSubclass(int classId, int subclassQName) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      view.removeSubclass(subclassQName);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int[] getReferencedClasses(int classId) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      return view.getReferencedClasses();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized ReferenceInfo[] getReferences(int classId) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      return view.getReferences();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized String getSourceFileName(int classId) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      return view.getSourceFileName();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void setSourceFileName(int classId, String name) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      view.setSourceFileName(name);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized boolean isRemote(int classId) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      return view.isRemote();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void setRemote(int classId, boolean remote) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      view.setRemote(remote);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getSuperQualifiedName(int classId) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      final int superQualifiedName = view.getSuperQualifiedName();
      return superQualifiedName;
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized String getPath(int classId) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      return view.getPath();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getGenericSignature(int classId) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      return view.getGenericSignature();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int[] getSuperInterfaces(int classId) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      return view.getSuperInterfaces();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getFlags(int classId) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      return view.getFlags();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void addReferencedClass(int classId, int referencedClassName) throws CacheCorruptedException {
    try {
      final ClassInfoView view = myViewPool.getClassInfoView(classId);
      view.addReferencedClass(referencedClassName);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int[] getFieldIds(int classDeclarationId) throws CacheCorruptedException{
    try {
      final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
      return view.getFieldIds();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int[] getMethodIds(int classDeclarationId) throws CacheCorruptedException{
    try {
      final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
      return view.getMethodIds();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void addClassReferencer(int classDeclarationId, int referencerQName) throws CacheCorruptedException {
    try {
      final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
      view.addReferencer(referencerQName);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void removeClassReferencer(int classDeclarationId, int referencerQName) throws CacheCorruptedException {
    try {
      final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
      view.removeReferencer(referencerQName);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getFieldName(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
      return view.getName();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getFieldDescriptor(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
      return view.getDescriptor();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getFieldGenericSignature(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
      return view.getGenericSignature();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getFieldFlags(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
      return view.getFlags();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized ConstantValue getFieldConstantValue(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
      return view.getConstantValue();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized AnnotationConstantValue[] getFieldRuntimeVisibleAnnotations(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
      return view.getRuntimeVisibleAnnotations();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized AnnotationConstantValue[] getFieldRuntimeInvisibleAnnotations(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
      return view.getRuntimeInvisibleAnnotations();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void addFieldReferencer(int fieldId, int referencerQName) throws CacheCorruptedException {
    try {
      final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldId);
      view.addReferencer(referencerQName);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void removeFieldReferencer(int fieldId, int referencerQName) throws CacheCorruptedException {
    try {
      final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldId);
      view.removeReferencer(referencerQName);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getMethodName(int methodDeclarationId) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      return view.getName();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getMethodDescriptor(int methodDeclarationId) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      return view.getDescriptor();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getMethodGenericSignature(int methodDeclarationId) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      return view.getGenericSignature();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int getMethodFlags(int methodDeclarationId) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      return view.getFlags();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized AnnotationConstantValue[] getMethodRuntimeVisibleAnnotations(int methodDeclarationId) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      return view.getRuntimeVisibleAnnotations();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized AnnotationConstantValue[] getMethodRuntimeInvisibleAnnotations(int methodDeclarationId) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      return view.getRuntimeInvisibleAnnotations();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized AnnotationConstantValue[][] getMethodRuntimeVisibleParamAnnotations(int methodDeclarationId) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      return view.getRuntimeVisibleParamAnnotations();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized AnnotationConstantValue[][] getMethodRuntimeInvisibleParamAnnotations(int methodDeclarationId) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      return view.getRuntimeInvisibleParamAnnotations();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized ConstantValue getAnnotationDefault(int methodDeclarationId) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      return view.getAnnotationDefault();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized boolean isConstructor(int methodDeclarationId) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      return view.isConstructor();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int[] getMethodThrownExceptions(int methodDeclarationId) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      return view.getThrownExceptions();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void addMethodReferencer(int methodDeclarationId, int referencerQName) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      view.addReferencer(referencerQName);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void removeMethodReferencer(int methodDeclarationId, int referencerQName) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      view.removeReferencer(referencerQName);
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized Dependency[] getBackDependencies(int classQName) throws CacheCorruptedException{
    final int classDeclarationId = getClassDeclarationId(classQName);
    if (classDeclarationId == UNKNOWN) {
      return null;
    }
    final Dependency[] dependencyArray;
    try {
      final TIntObjectHashMap dependencies = new TIntObjectHashMap();
      final ClassDeclarationView classDeclarationView = myViewPool.getClassDeclarationView(classDeclarationId);
      final int[] classReferencers = classDeclarationView.getReferencers();
      for (final int referencer : classReferencers) {
        if (referencer != classQName) { // skip self-dependencies
          addDependency(dependencies, referencer);
        }
      }

      final int[] fieldIds = classDeclarationView.getFieldIds();
      for (final int fieldId : fieldIds) {
        final FieldDeclarationView fieldDeclarationView = myViewPool.getFieldDeclarationView(fieldId);
        final int[] fieldReferencers = fieldDeclarationView.getReferencers();
        for (int referencer : fieldReferencers) {
          if (referencer != classQName) { // skip self-dependencies
            final Dependency dependency = addDependency(dependencies, referencer);
            dependency.addMemberInfo(createFieldInfo(fieldId));
          }
        }
      }

      final int[] methodIds = classDeclarationView.getMethodIds();
      for (final int methodId : methodIds) {
        final MethodDeclarationView methodDeclarationView = myViewPool.getMethodDeclarationView(methodId);
        final int[] methodReferencers = methodDeclarationView.getReferencers();
        for (int referencer : methodReferencers) {
          if (referencer != classQName) {
            final Dependency dependency = addDependency(dependencies, referencer);
            dependency.addMemberInfo(createMethodInfo(methodId));
          }
        }
      }
      dependencyArray = new Dependency[dependencies.size()];
      dependencies.forEachValue(new TObjectProcedure() {
        private int index = 0;
        public boolean execute(Object object) {
          dependencyArray[index++] = (Dependency)object;
          return true;
        }
      });
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
    return dependencyArray;
  }

  public synchronized FieldInfo createFieldInfo(final int fieldId) throws CacheCorruptedException {
    try {
      final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldId);
      return new FieldInfo(
        view.getName(),
        view.getDescriptor(),
        view.getGenericSignature(),
        view.getFlags(),
        view.getConstantValue(),
        view.getRuntimeVisibleAnnotations(),
        view.getRuntimeInvisibleAnnotations()
      );
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized MethodInfo createMethodInfo(final int methodId) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodId);
      return new MethodInfo(
        view.getName(),
        view.getDescriptor(),
        view.getGenericSignature(),
        view.getFlags(),
        view.getThrownExceptions(),
        view.isConstructor(),
        view.getRuntimeVisibleAnnotations(),
        view.getRuntimeInvisibleAnnotations(),
        view.getRuntimeVisibleParamAnnotations(),
        view.getRuntimeInvisibleParamAnnotations(),
        view.getAnnotationDefault()
      );
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized void forEachDeclaration(DeclarationProcessor processor) throws CacheCorruptedException {
    try {
      final TIntIntHashMap declarationsMap = getQNameToClassDeclarationIdMap();
      final int[] qNames = declarationsMap.keys();
      for (int idx = 0; idx < qNames.length; idx++) {
        final int qName = qNames[idx];
        if (!processor.process(new DeclarationInfo(qName))) {
          return;
        }
        final MemberDeclarationInfo[] memberDeclarations = getMemberDeclarations(qName);
        for (int i = 0; i < memberDeclarations.length; i++) {
          if (!processor.process(memberDeclarations[i])) {
            return;
          }
        }
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized MemberDeclarationInfo[] getMemberDeclarations(int classQName) throws CacheCorruptedException {
    final int classDeclarationId = getClassDeclarationId(classQName);
    final int[] fieldIds = getFieldIds(classDeclarationId);
    final int[] methodIds = getMethodIds(classDeclarationId);
    MemberDeclarationInfo[] infos = new MemberDeclarationInfo[fieldIds.length + methodIds.length];
    int index = 0;
    for (int idx = 0; idx < fieldIds.length; idx++) {
      infos[index++] = new MemberDeclarationInfo(classQName, createFieldInfo(fieldIds[idx]));
    }
    for (int idx = 0; idx < methodIds.length; idx++) {
      infos[index++] = new MemberDeclarationInfo(classQName, createMethodInfo(methodIds[idx]));
    }
    return infos;
  }

  private Dependency addDependency(TIntObjectHashMap container, int classQName) {
    Dependency dependency = (Dependency)container.get(classQName);
    if (dependency == null) {
      dependency = new Dependency(classQName);
      container.put(classQName, dependency);
    }
    return dependency;
  }

  public synchronized int[] getFieldReferencers(int fieldDeclarationId) throws CacheCorruptedException {
    try {
      final FieldDeclarationView view = myViewPool.getFieldDeclarationView(fieldDeclarationId);
      return view.getReferencers();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int[] getMethodReferencers(int methodDeclarationId) throws CacheCorruptedException {
    try {
      final MethodDeclarationView view = myViewPool.getMethodDeclarationView(methodDeclarationId);
      return view.getReferencers();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  public synchronized int[] getClassReferencers(int classDeclarationId) throws CacheCorruptedException {
    try {
      final ClassDeclarationView view = myViewPool.getClassDeclarationView(classDeclarationId);
      return view.getReferencers();
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  private void updateMemberDeclarations(int classQName, MemberInfo[] classMembers) throws CacheCorruptedException{
    try {
      final int classDeclarationId = getClassDeclarationId(classQName);

      final int[] fieldIds = getFieldIds(classDeclarationId);
      final int[] methodIds = getMethodIds(classDeclarationId);
      TObjectIntHashMap currentMembers = new TObjectIntHashMap();
      for (int idx = 0; idx < fieldIds.length; idx++) {
        final int fieldId = fieldIds[idx];
        currentMembers.put(createFieldInfo(fieldId), fieldId);
      }
      for (int idx = 0; idx < methodIds.length; idx++) {
        final int methodId = methodIds[idx];
        currentMembers.put(createMethodInfo(methodId), methodId);
      }

      TIntHashSet fieldsToRemove = new TIntHashSet(fieldIds);
      TIntHashSet methodsToRemove = new TIntHashSet(methodIds);

      for (int idx = 0; idx < classMembers.length; idx++) {
        final MemberInfo classMember = classMembers[idx];

        if (currentMembers.containsKey(classMember)) { // changed
          final int memberId = currentMembers.get(classMember);
          if (classMember instanceof FieldInfo) {
            fieldsToRemove.remove(memberId);
          }
          else if (classMember instanceof MethodInfo){
            methodsToRemove.remove(memberId);
          }
          putMember(classDeclarationId, memberId, classMember);
        }
        else { // added
          putMember(classDeclarationId, UNKNOWN, classMember);
        }
      }

      if (fieldsToRemove.size() > 0) {
        final int[] fieldsArray = fieldsToRemove.toArray();
        for (int idx = 0; idx < fieldsArray.length; idx++) {
          removeFieldDeclaration(classDeclarationId, fieldsArray[idx]);
        }
      }

      if (methodsToRemove.size() > 0) {
        final int[] methodsArray = methodsToRemove.toArray();
        for (int idx = 0; idx < methodsArray.length; idx++) {
          removeMethodDeclaration(classDeclarationId, methodsArray[idx]);
        }
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }

  private void removeMethodDeclaration(int classDeclarationId, int methodId) throws IOException, CacheCorruptedException {
    final ClassDeclarationView classDeclarationView = myViewPool.getClassDeclarationView(classDeclarationId);
    classDeclarationView.removeMethodId(methodId);
    final MethodDeclarationView methodDeclarationView = myViewPool.getMethodDeclarationView(methodId);
    //methodDeclarationView.removeRecord();
    myViewPool.removeMethodDeclarationRecord(methodDeclarationView);
  }


  private void removeFieldDeclaration(int classDeclarationId, int fieldId) throws IOException, CacheCorruptedException {
    final ClassDeclarationView classDeclarationView = myViewPool.getClassDeclarationView(classDeclarationId);
    classDeclarationView.removeFieldId(fieldId);
    final FieldDeclarationView fieldDeclarationView = myViewPool.getFieldDeclarationView(fieldId);
    //fieldDeclarationView.removeRecord();
    myViewPool.removeFieldDeclarationRecord(fieldDeclarationView);
  }

  public synchronized int putMember(final int classDeclarationId, int memberId, final MemberInfo classMember) throws CacheCorruptedException {
    try {
      if (classMember instanceof FieldInfo) {
        FieldInfo fieldInfo = (FieldInfo)classMember;
        final FieldDeclarationView view;
        view = myViewPool.getFieldDeclarationView(memberId);
        if (memberId == UNKNOWN) {
          memberId = view.getRecordId();
          final ClassDeclarationView classDeclarationView = myViewPool.getClassDeclarationView(classDeclarationId);
          classDeclarationView.addFieldId(memberId);
        }
        view.setName(fieldInfo.getName());
        view.setDescriptor(fieldInfo.getDescriptor());
        view.setGenericSignature(fieldInfo.getGenericSignature());
        view.setFlags(fieldInfo.getFlags());
        view.setConstantValue(fieldInfo.getConstantValue());
        view.setRuntimeVisibleAnnotations(fieldInfo.getRuntimeVisibleAnnotations());
        view.setRuntimeInvisibleAnnotations(fieldInfo.getRuntimeInvisibleAnnotations());
      }
      else if (classMember instanceof MethodInfo) {
        MethodInfo methodInfo = (MethodInfo)classMember;
        final MethodDeclarationView view = myViewPool.getMethodDeclarationView(memberId);
        if (memberId == UNKNOWN) {
          memberId = view.getRecordId();
          final ClassDeclarationView classDeclarationView = myViewPool.getClassDeclarationView(classDeclarationId);
          classDeclarationView.addMethodId(memberId);
        }
        view.setName(methodInfo.getName());
        view.setDescriptor(methodInfo.getDescriptor());
        view.setGenericSignature(methodInfo.getGenericSignature());
        view.setFlags(methodInfo.getFlags());
        view.setIsConstructor(methodInfo.isConstructor());
        view.setThrownExceptions(methodInfo.getThrownExceptions());
        view.setRuntimeVisibleAnnotations(methodInfo.getRuntimeVisibleAnnotations());
        view.setRuntimeInvisibleAnnotations(methodInfo.getRuntimeInvisibleAnnotations());
        view.setRuntimeVisibleParamAnnotations(methodInfo.getRuntimeVisibleParameterAnnotations());
        view.setRuntimeInvisibleParamAnnotations(methodInfo.getRuntimeInvisibleParameterAnnotations());
        view.setAnnotationDefault(methodInfo.getAnnotationDefault());
      }
      else {
        LOG.assertTrue(false, "Unknown member info: "+ classMember.getClass().getName());
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
    return memberId;
  }

  private void writeIndexMap(TIntIntHashMap map, File indexFile) throws IOException {
    if (!indexFile.exists()) {
      indexFile.createNewFile();
    }
    final DataOutputStream stream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));
    try {
      stream.writeInt(map.size());
      final IOException[] _ex = new IOException[] {null};
      map.forEachEntry(new TIntIntProcedure() {
        public boolean execute(int qName, int id) {
          try {
            stream.writeInt(qName);
            stream.writeInt(id);
          }
          catch (IOException e) {
            _ex[0]  = e;
            return false;
          }
          return true;
        }
      });
      if (_ex[0] != null) {
        throw _ex[0];
      }
    }
    finally {
      stream.close();
    }
  }

  private void readIndexMap(TIntIntHashMap map, File indexFile) throws IOException {
    if (indexFile.exists()) {
      DataInputStream stream = new DataInputStream(new ByteArrayInputStream(FileUtil.loadFileBytes(indexFile)));
      try {
        int size = stream.readInt();
        while (size-- > 0) {
          final int qName = stream.readInt();
          final int id = stream.readInt();
          map.put(qName, id);
        }
      }
      finally {
        stream.close();
      }
    }
  }

  public synchronized void wipe() {
    myViewPool.wipe();
    myQNameToClassDeclarationIdMap = null;
    myQNameToClassInfoIdMap = null;
    myClassInfosIndexFile.delete();
    myDeclarationsIndexFile.delete();
  }

  private synchronized TIntIntHashMap getQNameToClassInfoIdMap() throws IOException {
    if (myQNameToClassInfoIdMap == null) {
      myQNameToClassInfoIdMap = new TIntIntHashMap();
      readIndexMap(myQNameToClassInfoIdMap, myClassInfosIndexFile);
    }
    return myQNameToClassInfoIdMap;
  }

  private synchronized TIntIntHashMap getQNameToClassDeclarationIdMap() throws IOException {
    if (myQNameToClassDeclarationIdMap == null) {
      myQNameToClassDeclarationIdMap = new TIntIntHashMap();
      readIndexMap(myQNameToClassDeclarationIdMap, myDeclarationsIndexFile);
    }
    return myQNameToClassDeclarationIdMap;
  }

  public synchronized void removeClass(final int qName) throws CacheCorruptedException {
    try {
      final int classDeclarationId = getClassDeclarationId(qName);
      if (classDeclarationId != UNKNOWN) {
        final int[] fieldIds = getFieldIds(classDeclarationId);
        for (int idx = 0; idx < fieldIds.length; idx++) {
          final FieldDeclarationView fieldDeclarationView = myViewPool.getFieldDeclarationView(fieldIds[idx]);
          //fieldDeclarationView.removeRecord();
          myViewPool.removeFieldDeclarationRecord(fieldDeclarationView);
        }
        final int[] methodIds = getMethodIds(classDeclarationId);
        for (int idx = 0; idx < methodIds.length; idx++) {
          final MethodDeclarationView methodDeclarationView = myViewPool.getMethodDeclarationView(methodIds[idx]);
          //methodDeclarationView.removeRecord();
          myViewPool.removeMethodDeclarationRecord(methodDeclarationView);
        }

        final ClassDeclarationView classDeclarationView = myViewPool.getClassDeclarationView(classDeclarationId);
        //classDeclarationView.removeRecord();
        myViewPool.removeClassDeclarationRecord(classDeclarationView);
        getQNameToClassDeclarationIdMap().remove(qName);
      }

      final int classId = getClassId(qName);
      if (classId != UNKNOWN) {
        final ClassInfoView classInfoView = myViewPool.getClassInfoView(classId);
        //classInfoView.removeRecord();
        myViewPool.removeClassInfoRecord(classInfoView);
        getQNameToClassInfoIdMap().remove(qName);
      }
    }
    catch (Throwable e) {
      throw new CacheCorruptedException(e);
    }
  }
}
