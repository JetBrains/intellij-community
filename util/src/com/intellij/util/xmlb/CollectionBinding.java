package com.intellij.util.xmlb;

import java.lang.reflect.ParameterizedType;
import java.util.*;

class CollectionBinding extends AbstractCollectionBinding  {
  public CollectionBinding(ParameterizedType type, XmlSerializerImpl xmlSerializer, final Accessor accessor) {
    super((Class)type.getActualTypeArguments()[0], xmlSerializer, Constants.COLLECTION, accessor);
  }


  Object processResult(Collection result, Object target) {
    if (myAccessor == null) return result;
    
    assert target instanceof Collection : "Wrong target: " + target.getClass() + " in " + myAccessor;
    Collection c = (Collection)target;
    c.clear();
    //noinspection unchecked
    c.addAll(result);

    return target;
  }

  Iterable getIterable(Object o) {
    if (o instanceof Set) {
      Set set = (Set)o;
      return new TreeSet(set);
    }
    return (Collection)o;
  }

  protected String getCollectionTagName(final Object target) {
    if (target instanceof Set) {
      return Constants.SET;
    }
    else if (target instanceof List) {
      return Constants.LIST;
    }
    return super.getCollectionTagName(target);
  }

  protected Collection createCollection(final String tagName) {
    if (tagName.equals(Constants.SET)) return new HashSet();
    if (tagName.equals(Constants.LIST)) return new ArrayList();
    return super.createCollection(tagName);
  }
}
