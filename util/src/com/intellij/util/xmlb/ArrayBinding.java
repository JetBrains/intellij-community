package com.intellij.util.xmlb;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

class ArrayBinding extends AbstractCollectionBinding  {
  public ArrayBinding(Class elementClass, XmlSerializerImpl xmlSerializer) {
    super(elementClass, xmlSerializer, Constants.ARRAY);
  }


  @SuppressWarnings({"unchecked"})
  Object processResult(List result, Object target) {
    return result.toArray((Object[])Array.newInstance(getElementType(), result.size()));
  }

  Collection getCollection(Object o) {
    return Arrays.asList((Object[])o);
  }
}
