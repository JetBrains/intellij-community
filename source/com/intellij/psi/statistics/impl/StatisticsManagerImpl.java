
package com.intellij.psi.statistics.impl;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.codeStyle.StatisticsManagerEx;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ScrambledInputStream;
import com.intellij.util.ScrambledOutputStream;
import com.intellij.util.containers.StringInterner;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.util.*;

public class StatisticsManagerImpl extends StatisticsManager implements StatisticsManagerEx, ApplicationComponent {
  private static final int MAX_NAME_SUGGESTIONS_COUNT = 5;
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.statistics.impl.StatisticsManagerImpl");

  public String getComponentName() {
    return "StatisticsManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private static final int UNIT_COUNT = 997;

  private static final @NonNls String STORE_PATH = PathManager.getSystemPath() + File.separator + "stat";

  private SoftReference[] myUnits = new SoftReference[UNIT_COUNT];
  private HashSet<StatisticsUnit> myModifiedUnits = new HashSet<StatisticsUnit>();
  private final StringInterner myKeys = new StringInterner();

  private StatisticsManagerImpl() {
  }

  public int getMemberUseCount(PsiType qualifierType, PsiMember member, Map<PsiType, PsiType> normalizedItems) {
    if (qualifierType != null && normalizedItems != null) {
      if (normalizedItems.containsKey(qualifierType)) {
        qualifierType = normalizedItems.get(qualifierType);
      }
      else {
        normalizedItems.put(qualifierType, qualifierType = normalizeType(qualifierType, member.getManager()));
      }
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
      for (int i = 0; i < dimension; i++) {
        type = new PsiArrayType(type);
      }
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

  public void incNameUseCount(PsiType type, StatisticsManager.NameContext context, String name) {
    final String key1 = getMemberUseKey1(type);
    if(key1 == null) return;
    final StatisticsUnit unit = getUnit(getUnitNumber(key1));
    String key2 = getNameUseKey(context, name);
    final int count = unit.getData(key1, key2);
    unit.putData(key1, key2, count + 1);
    myModifiedUnits.add(unit);
  }

  public String[] getNameSuggestions(PsiType type, NameContext context, String prefix) {
    final List<String> suggestions = new ArrayList<String>();
    final String key1 = getMemberUseKey1(type);
    if(key1 == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    final StatisticsUnit unit = getUnit(getUnitNumber(key1));
    final String[] possibleNames = unit.getKeys2(key1);
    Arrays.sort(possibleNames, new Comparator<String>() {
      public int compare(final String o1, final String o2) {
        return - unit.getData(key1, o1) + unit.getData(key1, o2);
      }
    });

    for (int i = 0; i < possibleNames.length && suggestions.size() < MAX_NAME_SUGGESTIONS_COUNT; i++) {
      final String key2 = possibleNames[i];
      if(context != getNameUsageContext(key2)) continue;
      final String name = getName(key2);
      if(name == null || !name.startsWith(prefix)) continue;
      suggestions.add(name);
    }
    return suggestions.toArray(new String[suggestions.size()]);
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

    for (String key2 : keys2) {
      VariableKind variableKind1 = getVariableKindFromKey2(key2);
      if (variableKind1 != variableKind) continue;
      String name = getVariableNameFromKey2(key2);
      list.add(name);
    }

    return list.toArray(new String[list.size()]);
  }

  public void save() {
    if (!ApplicationManager.getApplication().isUnitTestMode()){
      for (StatisticsUnit unit : myModifiedUnits) {
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
      unit = new StatisticsUnit(unitNumber, myKeys);
    }
    myUnits[unitNumber] = new SoftReference(unit);
    return unit;
  }

  private StatisticsUnit loadUnit(int unitNumber) {
    StatisticsUnit unit = new StatisticsUnit(unitNumber, myKeys);
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
        PsiBundle.message("error.saving.statistics", e.getLocalizedMessage()),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
    }
  }

  private static int getUnitNumber(String key1) {
    return Math.abs(key1.hashCode()) % UNIT_COUNT;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String getMemberUseKey1(PsiType qualifierType) {
    return "member#" + (qualifierType == null ? "" : qualifierType.getCanonicalText());
  }

  private static @NonNls String getMemberUseKey2(PsiMember member) {
    if (member instanceof PsiMethod){
      PsiMethod method = (PsiMethod)member;
      @NonNls StringBuffer buffer = new StringBuffer();
      buffer.append("method#");
      buffer.append(method.getName());
      PsiParameter[] parms = method.getParameterList().getParameters();
      for (PsiParameter parm : parms) {
        buffer.append("#");
        buffer.append(parm.getType().getPresentableText());
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

  private String getNameUseKey(final NameContext context, final String name) {
    final @NonNls StringBuffer buffer = new StringBuffer();
    buffer.append("variableName#");
    buffer.append(context.name());
    buffer.append('#');
    buffer.append(name);
    return buffer.toString();
  }

  private String getVariableNameUseKey1(String propertyName, PsiType type) {
    @NonNls StringBuffer buffer = new StringBuffer();
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

  private NameContext getNameUsageContext(String key2){
    final int startIndex = key2.indexOf("#");
    LOG.assertTrue(startIndex >= 0);
    @NonNls String s = key2.substring(0, startIndex);
    if(!"variableName".equals(s)) return null;
    final int index = key2.indexOf("#", startIndex + 1);
    s = key2.substring(startIndex + 1, index);
    return NameContext.valueOf(s);
  }

  private String getName(String key2){
    final int startIndex = key2.indexOf("#");
    LOG.assertTrue(startIndex >= 0);
    @NonNls String s = key2.substring(0, startIndex);
    if(!"variableName".equals(s)) return null;
    final int index = key2.indexOf("#", startIndex + 1);
    LOG.assertTrue(index >= 0);
    return key2.substring(index + 1);
  }

  private VariableKind getVariableKindFromKey2(String key2){
    int index = key2.indexOf("#");
    LOG.assertTrue(index >= 0);
    String s = key2.substring(0, index);
    return VariableKind.valueOf(s);
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
          PsiBundle.message("error.saving.statistic.failed.to.create.folder", STORE_PATH),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private String getPathToUnit(int unitNumber) {
    return STORE_PATH + File.separator + "unit." + unitNumber;
  }
}