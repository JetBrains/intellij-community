package org.jetbrains.plugins.textmate.plist;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class PListValue {
  private final Object myValue;
  private final PlistValueType myType;

  PListValue(Object value, PlistValueType type) {
    myValue = value;
    myType = type;
  }

  @Nullable
  public Object getValue() {
    return myValue;
  }

  public PlistValueType getType() {
    return myType;
  }

  @NotNull
  public Plist getPlist() {
    return myType == PlistValueType.DICT
           ? (Plist)myValue
           : Plist.EMPTY_PLIST;
  }

  @SuppressWarnings("unchecked")
  @NotNull
  public List<PListValue> getArray() {
    return myType == PlistValueType.ARRAY ? (List<PListValue>)myValue : Collections.emptyList();
  }

  @NotNull
  public List<String> getStringArray() {
    List<PListValue> array = getArray();
    List<String> result = new ArrayList<>(array.size());
    for (PListValue value : array) {
      result.add(value.getString());
    }
    return result;
  }

  public String getString() {
    return myType == PlistValueType.STRING
           ? (String)myValue
           : myValue.toString();
  }


  public static PListValue value(Object value, PlistValueType type) {
    if (value == null) {
      return new NullablePListValue(type);
    }
    return new PListValue(value, type);
  }

  public static PListValue string(String value) {
    return value(value, PlistValueType.STRING);
  }

  public static PListValue bool(Boolean value) {
    return value(value, PlistValueType.BOOLEAN);
  }

  public static PListValue integer(Long value) {
    return value(value, PlistValueType.INTEGER);
  }

  public static PListValue real(Double value) {
    return value(value, PlistValueType.REAL);
  }

  public static PListValue date(Date value) {
    return value(value, PlistValueType.DATE);
  }

  public static PListValue array(List<PListValue> value) {
    return value(value, PlistValueType.ARRAY);
  }

  public static PListValue array(PListValue... value) {
    return value(ContainerUtil.newArrayList(value), PlistValueType.ARRAY);
  }

  public static PListValue dict(Plist value) {
    return value(value, PlistValueType.DICT);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PListValue value = (PListValue)o;

    if (myType != value.myType) return false;
    if (myValue != null ? !myValue.equals(value.myValue) : value.myValue != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myValue != null ? myValue.hashCode() : 0;
    result = 31 * result + myType.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "PListValue{" +
           "myValue=" + myValue +
           ", myType=" + myType +
           '}';
  }

  private static class NullablePListValue extends PListValue {
    private NullablePListValue(PlistValueType type) {
      super(null, type);
    }

    @NotNull
    @Override
    public Plist getPlist() {
      return Plist.EMPTY_PLIST;
    }

    @NotNull
    @Override
    public List<PListValue> getArray() {
      return Collections.emptyList();
    }

    @NotNull
    @Override
    public List<String> getStringArray() {
      return Collections.emptyList();
    }

    @Override
    @Nullable
    public String getString() {
      return null;
    }
  }
}
