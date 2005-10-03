package com.intellij.util.config;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;
import java.util.List;

public interface Externalizer<T> {
  @NonNls String VALUE_ATTRIBUTE = "value";
  Externalizer<String> STRING = new BaseExternalizer<String>(){
    public String readValue(Element dataElement) {
      return dataElement.getAttributeValue(VALUE_ATTRIBUTE);
    }
  };
  Externalizer<Integer> INTEGER = new BaseExternalizer<Integer>() {
    public Integer readValue(Element dataElement) {
      try {
        return new Integer(dataElement.getAttributeValue(VALUE_ATTRIBUTE));
      } catch(NumberFormatException e) {
        return null;
      }
    }
  };
  Externalizer<Storage> STORAGE = new StorageExternalizer();

  abstract class BaseExternalizer<T> implements Externalizer<T> {

    public void writeValue(Element dataElement, T value) {
      dataElement.setAttribute(VALUE_ATTRIBUTE, value.toString());
    }
  }
  Externalizer<Boolean> BOOLEAN = new BaseExternalizer<Boolean>() {
    public Boolean readValue(Element dataElement) {
      return Boolean.valueOf(dataElement.getAttributeValue(VALUE_ATTRIBUTE));
    }
  };

  T readValue(Element dataElement) throws InvalidDataException;

  void writeValue(Element dataElement, T value) throws WriteExternalException;

  class FactoryBased<T extends JDOMExternalizable> implements Externalizer<T> {
    private final Factory<T> myFactory;

    public FactoryBased(Factory<T> factory) {
      myFactory = factory;
    }

    public T readValue(Element dataElement) throws InvalidDataException {
      T data = myFactory.create();
      data.readExternal(dataElement);
      return data;
    }

    public void writeValue(Element dataElement, T value) throws WriteExternalException {
      value.writeExternal(dataElement);
    }

    public static <T extends JDOMExternalizable> FactoryBased<T> create(Factory<T> factory) {
      return new FactoryBased<T>(factory);
    }
  }

  class StorageExternalizer implements Externalizer<Storage> {
    @NonNls private static final String ITEM_TAG = "item";
    @NonNls private static final String KEY_ATTR = "key";
    @NonNls private static final String VALUE_ATTR = "value";

    public Storage readValue(Element dataElement) throws InvalidDataException {
      Storage.MapStorage storage = new Storage.MapStorage();
      List<Element> children = dataElement.getChildren(ITEM_TAG);
      for (Iterator<Element> iterator = children.iterator(); iterator.hasNext();) {
        Element element = iterator.next();
        storage.put(element.getAttributeValue(KEY_ATTR), element.getAttributeValue(VALUE_ATTR));
      }
      return storage;
    }

    public void writeValue(Element dataElement, Storage storage) throws WriteExternalException {
      Iterator<String> keys = ((Storage.MapStorage)storage).getKeys();
      while (keys.hasNext()) {
        String key = keys.next();
        String value = storage.get(key);
        Element element = new Element(ITEM_TAG);
        element.setAttribute(KEY_ATTR, key);
        if (value != null) element.setAttribute(VALUE_ATTR, value);
        dataElement.addContent(element);
      }
    }
  }
}
