package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.DOMUtil;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.output.DOMOutputter;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import javax.xml.parsers.ParserConfigurationException;


@SuppressWarnings({"deprecation"})
class DefaultStateSerializer {
  private static final SerializationFilter ourSerializationFilter = new SkipDefaultValuesSerializationFilters();

  private DefaultStateSerializer() {
  }

  static Element serializeState(Object state) throws ParserConfigurationException, WriteExternalException {
    if (state instanceof JDOMExternalizable) {
      JDOMExternalizable jdomExternalizable = (JDOMExternalizable)state;

      final org.jdom.Element element = new org.jdom.Element("temp_element");
      jdomExternalizable.writeExternal(element);
      final Element domElement;
      try {
        final Document d = new Document();
        d.addContent(element);
        domElement = new DOMOutputter().output(d).getDocumentElement();
      }
      catch (JDOMException e1) {
        throw new RuntimeException(e1);
      }

      return domElement;
    }
    else {
      return  XmlSerializer.serialize(state, DOMUtil.createDocument(), ourSerializationFilter);
    }
  }

  @SuppressWarnings({"unchecked"})
  @Nullable
  static <T> T deserializeState(@Nullable Element stateElement, Class <T> stateClass, @Nullable T mergeInto) throws StateStorage.StateStorageException {
    if (stateElement == null) return mergeInto;

    if (stateClass.equals(org.jdom.Element.class)) {
      assert mergeInto == null;
      return (T)JDOMUtil.convertFromDOM(stateElement);
    }
    else if (JDOMExternalizable.class.isAssignableFrom(stateClass)) {
      assert mergeInto == null;
      try {
        final T t = stateClass.newInstance();
        try {
          ((JDOMExternalizable)t).readExternal(JDOMUtil.convertFromDOM(stateElement));
          return t;
        }
        catch (InvalidDataException e) {
          throw new StateStorage.StateStorageException(e);
        }
      }
      catch (InstantiationException e) {
        throw new StateStorage.StateStorageException(e);
      }
      catch (IllegalAccessException e) {
        throw new StateStorage.StateStorageException(e);
      }
    }
    else {
      if (mergeInto == null) {
        return XmlSerializer.deserialize(stateElement, stateClass);
      }
      else {
        XmlSerializer.deserializeInto(mergeInto, stateElement);
        return mergeInto;
      }
    }
  }

}
