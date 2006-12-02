package com.intellij.util.xmlb;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

class ArrayBinding extends AbstractCollectionBinding  {
  private com.intellij.util.xmlb.Array myArrayAnnotation = null;

  public ArrayBinding(XmlSerializerImpl xmlSerializer, final Class<?> valueClass, final Accessor accessor) {
    super(valueClass.getComponentType(), xmlSerializer, Constants.ARRAY);

    if (accessor != null) {
      myArrayAnnotation = XmlSerializerImpl.findAnnotation(accessor.getAnnotations(), com.intellij.util.xmlb.Array.class);
    }

    if (myArrayAnnotation != null) {
      if (!myArrayAnnotation.surroundWithTag() &&
          (myArrayAnnotation.elementTag() == null || myArrayAnnotation.elementTag().equals(Constants.OPTION))) {
        throw new XmlSerializationException("If surround with tag is turned off, element tag must be specified for: " + accessor);
      }
    }
  }


  protected Binding createElementTagWrapper(final Binding elementBinding) {
    if (myArrayAnnotation == null) return super.createElementTagWrapper(elementBinding);
    
    return new TagBindingWrapper(elementBinding,
                                 myArrayAnnotation.elementTag() != null ? myArrayAnnotation.elementTag() : Constants.OPTION,
                                 myArrayAnnotation.elementValueAttribute() != null ? myArrayAnnotation.elementValueAttribute() : Constants.VALUE);
  }

  @SuppressWarnings({"unchecked"})
  Object processResult(List result, Object target) {
    return result.toArray((Object[])Array.newInstance(getElementType(), result.size()));
  }

  Collection getCollection(Object o) {
    return Arrays.asList((Object[])o);
  }

  @Nullable
  public String getTagName() {
    if (myArrayAnnotation == null || myArrayAnnotation.surroundWithTag()) return super.getTagName();
    return null;
  }
}
