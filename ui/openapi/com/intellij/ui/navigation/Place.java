package com.intellij.ui.navigation;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.options.Configurable;
import com.intellij.util.ui.update.ComparableObject;
import com.intellij.util.ui.update.ComparableObjectCheck;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class Place implements ComparableObject {

  private LinkedHashMap<String, Object> myPath = new LinkedHashMap<String, Object>();

  public final Object[] getEqualityObjects() {
    return new Object[] {myPath};
  }

  public final boolean equals(final Object obj) {
    return ComparableObjectCheck.equals(this, obj);
  }

  public final int hashCode() {
    return ComparableObjectCheck.hashCode(this, super.hashCode());
  }

  public Place putPath(String name, Object value) {
    myPath.put(name, value);
    return this;
  }

  public @Nullable
  Object getPath(String name) {
    return myPath.get(name);
  }

  public Place cloneForElement(final String name, Object value) {
    final Place clone = new Place();
    clone.myPath = (LinkedHashMap<String, Object>)myPath.clone();
    clone.myPath.put(name, value);
    return clone;
  }

  public void copyFrom(final Place from) {
    myPath = (LinkedHashMap<String, Object>)from.myPath.clone();
  }


  public interface Navigator {

    ActionCallback navigateTo(@Nullable Place place);

    void queryPlace(@NotNull Place place);

  }

  public static ActionCallback goFurther(Object object, Place place) {
    if (object instanceof Navigator) {
      return ((Navigator)object).navigateTo(place);
    } else {
      return new ActionCallback.Done();
    }
  }

  public static void queryFurther(final Object object, final Place place) {
    if (object instanceof Navigator) {
      ((Navigator)object).queryPlace(place);
    }
  }

}
