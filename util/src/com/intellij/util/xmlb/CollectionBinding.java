package com.intellij.util.xmlb;

import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.List;

class CollectionBinding extends AbstractCollectionBinding  {
  public CollectionBinding(ParameterizedType type, XmlSerializerImpl xmlSerializer, final Accessor accessor) {
    super((Class)type.getActualTypeArguments()[0], xmlSerializer, Constants.COLLECTION, accessor);
  }


  Object processResult(List result, Object target) {
    assert target instanceof Collection : "Wrong target: " + target.getClass() + " in " + myAccessor;
    Collection c = (Collection)target;
    c.clear();
    //noinspection unchecked
    c.addAll(result);

    return target;
  }

  Iterable getIterable(Object o) {
    return (Collection)o;
  }
}
