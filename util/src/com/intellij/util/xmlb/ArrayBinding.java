package com.intellij.util.xmlb;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;

class ArrayBinding extends AbstractCollectionBinding  {

  public ArrayBinding(XmlSerializerImpl xmlSerializer, final Class<?> valueClass, final Accessor accessor) {
    super(valueClass.getComponentType(), xmlSerializer, Constants.ARRAY, accessor);
  }



  @SuppressWarnings({"unchecked"})
  Object processResult(Collection result, Object target) {
    return result.toArray((Object[])Array.newInstance(getElementType(), result.size()));
  }

  Iterable getIterable(Object o) {
    return o != null ? Arrays.asList((Object[])o) : null;
  }
}
