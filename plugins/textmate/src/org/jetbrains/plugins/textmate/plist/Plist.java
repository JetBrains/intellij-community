package org.jetbrains.plugins.textmate.plist;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.unmodifiableMap;

public class Plist {
  public static final Plist EMPTY_PLIST = fromMap(new HashMap<>());

  public static SimpleDateFormat dateFormatter() {
    return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
  }

  private final Map<String, PListValue> myMap;

  public static Plist fromMap(Map<String, PListValue> map) {
    return new Plist(unmodifiableMap(map));
  }

  public Plist() {
    this(new LinkedHashMap<>());
  }

  private Plist(Map<String, PListValue> map) {
    myMap = map;
  }

  public void setEntry(@NotNull String key, @Nullable PListValue value) {
    if (value == null) {
      myMap.remove(key);
    }
    else {
      myMap.put(key, value);
    }
  }

  @Nullable
  public PListValue getPlistValue(@NotNull String key) {
    return getPlistValue(key, null);
  }

  @Contract("_,!null -> !null")
  public PListValue getPlistValue(@NotNull String key, @Nullable Object defValue) {
    PListValue result = myMap.get(key);
    if (result != null) {
      return result;
    }
    if (defValue != null) {
      return new PListValue(defValue, PlistValueType.fromObject(defValue));
    }
    return null;
  }

  public boolean contains(@NotNull String key) {
    return myMap.containsKey(key);
  }

  public Set<Map.Entry<String, PListValue>> entries() {
    return myMap.entrySet();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Plist plist = (Plist)o;
    if (!myMap.equals(plist.myMap)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return myMap.hashCode();
  }
}
