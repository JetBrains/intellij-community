/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import org.jdom.Element;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Iterator;

/**
 * @author mike
 */
public class DefaultJDOMExternalizer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.DefaultJDOMExternalizer");

  public interface JDOMFilter{
    boolean isAccept(Field field);
  }

  public static void writeExternal(Object data, Element parentNode) throws WriteExternalException {
    writeExternal(data, parentNode, null);
  }

  public static void writeExternal(Object data, Element parentNode, JDOMFilter filter) throws WriteExternalException {
    Field[] fields = data.getClass().getFields();
    for(int i = 0; i < fields.length; i++){
      Field field = fields[i];
      if (field.getName().indexOf('$') >= 0) continue;
      int modifiers = field.getModifiers();
      String value = null;
      if ((modifiers & Modifier.PUBLIC) == 0 || (modifiers & Modifier.STATIC) != 0) continue;
      field.setAccessible(true); // class might be non-public
      Class type = field.getType();
      if(filter != null && !filter.isAccept(field)){
        continue;
      }
      try{
        if (type.isPrimitive()){
          if (type.equals(byte.class)){
            value = Byte.toString(field.getByte(data));
          }
          else if (type.equals(short.class)){
            value = Short.toString(field.getShort(data));
          }
          else if (type.equals(int.class)){
            value = Integer.toString(field.getInt(data));
          }
          else if (type.equals(long.class)){
            value = Long.toString(field.getLong(data));
          }
          else if (type.equals(float.class)){
            value = Float.toString(field.getFloat(data));
          }
          else if (type.equals(double.class)){
            value = Double.toString(field.getDouble(data));
          }
          else if (type.equals(char.class)){
            value = "" + field.getChar(data);
          }
          else if (type.equals(boolean.class)){
            value = Boolean.toString(field.getBoolean(data));
          }
          else{
            continue;
          }
        }
        else if (type.equals(String.class)){
          value = (String)field.get(data);
        }
        else if (type.equals(Color.class)){
          Color color = (Color)field.get(data);
          if (color != null) {
            value = Integer.toString(color.getRGB() & 0xFFFFFF, 16);
          }
        }
        else if (JDOMExternalizable.class.isAssignableFrom(type)){
           Element element = new Element("option");
           parentNode.addContent(element);
           element.setAttribute("name", field.getName());
           JDOMExternalizable domValue = (JDOMExternalizable)field.get(data);
           if (domValue != null){
             Element valueElement = new Element("value");
             element.addContent(valueElement);
             domValue.writeExternal(valueElement);
           }
           continue;
         }
        else{
          LOG.debug("Wrong field type: " + type);
          continue;
        }
      }
      catch(IllegalAccessException e){
        continue;
      }
      Element element = new Element("option");
      parentNode.addContent(element);
      element.setAttribute("name", field.getName());
      if (value != null){
        element.setAttribute("value", value);
      }
    }
  }

  public static void readExternal(Object data, Element parentNode) throws InvalidDataException{
    if (parentNode == null) return;

    for (Iterator i = parentNode.getChildren("option").iterator(); i.hasNext();) {
      Element e = (Element)i.next();

      String fieldName = e.getAttributeValue("name");
      if (fieldName == null){
        throw new InvalidDataException();
      }
      try{
        Field field = data.getClass().getField(fieldName);
        Class type = field.getType();
        int modifiers = field.getModifiers();
        if ((modifiers & Modifier.PUBLIC) == 0 || (modifiers & Modifier.STATIC) != 0 || (modifiers & Modifier.FINAL) != 0) continue;
        field.setAccessible(true); // class might be non-public
        String value = e.getAttributeValue("value");
        if (type.isPrimitive()){
          if (value == null){
            throw new InvalidDataException();
          }
          if (type.equals(byte.class)){
            try{
              field.setByte(data, Byte.parseByte(value));
            }
            catch(NumberFormatException ex){
              throw new InvalidDataException();
            }
          }
          else if (type.equals(short.class)){
            try{
              field.setShort(data, Short.parseShort(value));
            }
            catch(NumberFormatException ex){
              throw new InvalidDataException();
            }
          }
          else if (type.equals(int.class)){
            try{
              field.setInt(data, Integer.parseInt(value));
            }
            catch(NumberFormatException ex){
              throw new InvalidDataException();
            }
          }
          else if (type.equals(long.class)){
            try{
              field.setLong(data, Long.parseLong(value));
            }
            catch(NumberFormatException ex){
              throw new InvalidDataException();
            }
          }
          else if (type.equals(float.class)){
            try{
              field.setFloat(data, Float.parseFloat(value));
            }
            catch(NumberFormatException ex){
              throw new InvalidDataException();
            }
          }
          else if (type.equals(double.class)){
            try{
              field.setDouble(data, Double.parseDouble(value));
            }
            catch(NumberFormatException ex){
              throw new InvalidDataException();
            }
          }
          else if (type.equals(char.class)){
            if (value.length() != 1){
              throw new InvalidDataException();
            }
            field.setChar(data, value.charAt(0));
          }
          else if (type.equals(boolean.class)){
            if (value.equals("true")){
              field.setBoolean(data, true);
            }
            else if (value.equals("false")){
              field.setBoolean(data, false);
            }
            else{
              throw new InvalidDataException();
            }
          }
          else{
            throw new InvalidDataException();
          }
        }
        else if (type.equals(String.class)){
          field.set(data, value);
        }
        else if (type.equals(Color.class)){
          if (value != null){
            try{
              int rgb = Integer.parseInt(value, 16);
              field.set(data, new Color(rgb));
            }
            catch(NumberFormatException ex){
              System.out.println("value=" + value);
              throw new InvalidDataException();
            }
          }
          else{
            field.set(data, null);
          }
        }
        else if (JDOMExternalizable.class.isAssignableFrom(type)){
          JDOMExternalizable object = null;

          for (Iterator j = e.getChildren("value").iterator(); j.hasNext();) {
            Element el = (Element)j.next();
            object = (JDOMExternalizable)type.newInstance();
            object.readExternal(el);
          }

          field.set(data, object);
        }
        else{
          throw new InvalidDataException("wrong type: " + type);
        }
      }
      catch(NoSuchFieldException ex){
        continue;
      }
      catch(SecurityException ex){
        throw new InvalidDataException();
      }
      catch(IllegalAccessException ex){
        ex.printStackTrace();
        throw new InvalidDataException();
      }
      catch (InstantiationException ex) {
        throw new InvalidDataException();
      }
    }
  }
}
