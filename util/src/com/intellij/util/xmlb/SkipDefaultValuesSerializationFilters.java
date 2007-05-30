package com.intellij.util.xmlb;

import com.intellij.openapi.util.Comparing;

import java.util.HashMap;
import java.util.Map;

public class SkipDefaultValuesSerializationFilters implements SerializationFilter {
  private Map<Class, Object> myDefaultBeans = new HashMap<Class, Object>();

  public boolean accepts(final Accessor accessor, final Object bean) {
    Object defaultBean = getDefaultBean(bean);

    return !Comparing.equal(accessor.read(bean), accessor.read(defaultBean));
  }

  private Object getDefaultBean(final Object bean) {
    Class c = bean.getClass();
    Object o = myDefaultBeans.get(c);

    if (o == null) {
      try {
        o = c.newInstance();
        configure(o);
      }
      catch (InstantiationException e) {
        throw new XmlSerializationException(e);
      }
      catch (IllegalAccessException e) {
        throw new XmlSerializationException(e);
      }

      myDefaultBeans.put(c, o);
    }

    return o;
  }

  protected void configure(final Object o) {
    //todo put your own default object configuration here
  }
}
