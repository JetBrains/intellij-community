
package com.intellij.psi.statistics.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.codeStyle.StatisticsManagerEx;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.util.ScrambledInputStream;
import com.intellij.util.ScrambledOutputStream;

import java.io.*;
import java.lang.ref.SoftReference;
import java.util.*;

public class StatisticsManagerImpl extends StatisticsManager implements StatisticsManagerEx, ApplicationComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.statistics.impl.StatisticsManagerImpl");

  public String getComponentName() {
    return "StatisticsManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private static final int UNIT_COUNT = 997;

  private static final String STORE_PATH = PathManager.getSystemPath() + File.separator + "stat";

  private SoftReference[] myUnits = new SoftReference[UNIT_COUNT];
  private HashSet<StatisticsUnit> myModifiedUnits = new HashSet<StatisticsUnit>();

  private StatisticsManagerImpl() {
  }

  public int getMemberUseCount(PsiType qualifierType, PsiMember member, Map<PsiType, PsiType> normalizedItems) {
    if (qualifierType != null && normalizedItems != null) {
      if(normalizedItems.containsKey(qualifierType))
        qualifierType = normalizedItems.get(qualifierType);
      else
        normalizedItems.put(qualifierType, qualifierType =  normalizeType(qualifierType, member.getManager()));
    }
    String key1 = getMemberUseKey1(qualifierType);
    if (key1 == null) return 0;
    String key2 = getMemberUseKey2(member);
    if (key2 == null) return 0;
    int unitNumber = getUnitNumber(key1);
    StatisticsUnit unit = getUnit(unitNumber);
    return unit.getData(key1, key2);
  }

  private PsiType normalizeType(PsiType type, PsiManager manager){
    if(type instanceof PsiClassType){
      return manager.getElementFactory().createType(((PsiClassType) type).resolve());
    }
    else if(type instanceof PsiArrayType){
      final PsiType componentType = normalizeType(((PsiArrayType) type).getComponentType(), manager);
      final int dimension = type.getArrayDimensions();
      type = componentType;
      for(int i = 0; i < dimension; i++)
        type = new PsiArrayType(type);
      return type;
    }

    return type;
  }

  public void incMemberUseCount(PsiType qualifierType, PsiMember member) {
    qualifierType = normalizeType(qualifierType, member.getManager());
    String key1 = getMemberUseKey1(qualifierType);
    if (key1 == null) return;
    String key2 = getMemberUseKey2(member);
    if (key2 == null) return;
    int unitNumber = getUnitNumber(key1);
    StatisticsUnit unit = getUnit(unitNumber);
    int count = unit.getData(key1, key2);
    unit.putData(key1, key2, count + 1);
    myModifiedUnits.add(unit);
  }

  public int getVariableNameUseCount(String name, VariableKind variableKind, String propertyName, PsiType type) {
    String key1 = getVariableNameUseKey1(propertyName, type);
    String key2 = getVariableNameUseKey2(variableKind, name);
    int unitNumber = getUnitNumber(key1);
    StatisticsUnit unit = getUnit(unitNumber);
    return unit.getData(key1, key2);
  }

  public void incVariableNameUseCount(String name, VariableKind variableKind, String propertyName, PsiType type) {
    String key1 = getVariableNameUseKey1(propertyName, type);
    String key2 = getVariableNameUseKey2(variableKind, name);
    int unitNumber = getUnitNumber(key1);
    StatisticsUnit unit = getUnit(unitNumber);
    int count = unit.getData(key1, key2);
    unit.putData(key1, key2, count + 1);
    myModifiedUnits.add(unit);
  }

  public String[] getAllVariableNamesUsed(VariableKind variableKind, String propertyName, PsiType type) {
    String key1 = getVariableNameUseKey1(propertyName, type);
    int unitNumber = getUnitNumber(key1);
    StatisticsUnit unit = getUnit(unitNumber);
    String[] keys2 = unit.getKeys2(key1);

    ArrayList<String> list = new ArrayList<String>();

    for(int i = 0; i < keys2.length; i++){
      String key2 = keys2[i];
      VariableKind variableKind1 = getVariableKindFromKey2(key2);
      if (variableKind1 != variableKind) continue;
      String name = getVariableNameFromKey2(key2);
      list.add(name);
    }

    return list.toArray(new String[list.size()]);
  }

  public void save() {
    if (!ApplicationManager.getApplication().isUnitTestMode()){
      for(Iterator<StatisticsUnit> iterator = myModifiedUnits.iterator(); iterator.hasNext();){
        StatisticsUnit unit = iterator.next();
        saveUnit(unit.getNumber());
      }
    }
    myModifiedUnits.clear();
  }

  private StatisticsUnit getUnit(int unitNumber) {
    SoftReference ref = myUnits[unitNumber];
    if (ref != null){
      StatisticsUnit unit = (StatisticsUnit)ref.get();
      if (unit != null) return unit;
    }
    StatisticsUnit unit = loadUnit(unitNumber);
    if (unit == null){
      unit = new StatisticsUnit(unitNumber);
    }
    myUnits[unitNumber] = new SoftReference(unit);
    return unit;
  }

  private StatisticsUnit loadUnit(int unitNumber) {
    StatisticsUnit unit = new StatisticsUnit(unitNumber);
    if (!ApplicationManager.getApplication().isUnitTestMode()){
      String path = getPathToUnit(unitNumber);
      try{
        InputStream in = new BufferedInputStream(new FileInputStream(path));
        in = new ScrambledInputStream(in);
        try{
          unit.read(in);
        }
        finally{
          in.close();
        }
      }
      catch(IOException e){
      }
      catch(WrongFormatException e){
      }
    }
    return unit;
  }

  private void saveUnit(int unitNumber){
    if (!createStoreFolder()) return;
    StatisticsUnit unit = getUnit(unitNumber);
    String path = getPathToUnit(unitNumber);
    try{
      OutputStream out = new BufferedOutputStream(new FileOutputStream(path));
      out = new ScrambledOutputStream(out);
      try {
        unit.write(out);
      }
      finally{
        out.close();
      }
    }
    catch(IOException e){
      Messages.showMessageDialog(
        "Error saving system information: " + e.getMessage(),
        "Error",
        Messages.getErrorIcon()
      );
    }
  }

  private static int getUnitNumber(String key1) {
    return Math.abs(key1.hashCode()) % UNIT_COUNT;
  }

  private static String getMemberUseKey1(PsiType qualifierType) {
    return "member#" + (qualifierType == null ? "" : qualifierType.getCanonicalText());
  }

  private static String getMemberUseKey2(PsiMember member) {
    if (member instanceof PsiMethod){
      PsiMethod method = (PsiMethod)member;
      StringBuffer buffer = new StringBuffer();
      buffer.append("method#");
      buffer.append(method.getName());
      PsiParameter[] parms = method.getParameterList().getParameters();
      for(int i = 0; i < parms.length; i++){
        buffer.append("#");
        buffer.append(parms[i].getType().getPresentableText());
      }
      return buffer.toString();
    }
    else if (member instanceof PsiField){
      return "field#" + ((PsiField)member).getName();
    }
    else if (member instanceof PsiClass){
      return "class#" + ((PsiClass)member).getQualifiedName();
    }
    else{
      return null;
    }
  }

  private String getVariableNameUseKey1(String propertyName, PsiType type) {
    StringBuffer buffer = new StringBuffer();
    buffer.append("variableName#");
    if (propertyName != null){
      buffer.append(propertyName);
    }
    buffer.append("#");
    if (type != null){
      buffer.append(type.getCanonicalText());
    }
    return buffer.toString();
  }

  private String getVariableNameUseKey2(VariableKind kind, String name) {
    StringBuffer buffer = new StringBuffer();
    buffer.append(kind);
    buffer.append("#");
    buffer.append(name);
    return buffer.toString();
  }

  private VariableKind getVariableKindFromKey2(String key2){
    int index = key2.indexOf("#");
    LOG.assertTrue(index >= 0);
    String s = key2.substring(0, index);
    return VariableKind.fromString(s);
  }

  private String getVariableNameFromKey2(String key2){
    int index = key2.indexOf("#");
    LOG.assertTrue(index >= 0);
    return key2.substring(index + 1);
  }

  private boolean createStoreFolder(){
    File homeFile = new File(STORE_PATH);
    if (!homeFile.exists()){
      if (!homeFile.mkdirs()){
        Messages.showMessageDialog(
          "Cannot create folder " + STORE_PATH + " to save system information.",
          "Error",
          Messages.getErrorIcon()
        );
        return false;
      }
    }
    return true;
  }

  private String getPathToUnit(int unitNumber) {
    return STORE_PATH + File.separator + "unit." + unitNumber;
  }
}