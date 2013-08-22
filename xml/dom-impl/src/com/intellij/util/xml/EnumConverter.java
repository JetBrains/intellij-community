package com.intellij.util.xml;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * @author peter
 */
public class EnumConverter<T extends Enum> extends ResolvingConverter<T>{
  private static final ConcurrentFactoryMap<Class,EnumConverter> ourCache = new ConcurrentFactoryMap<Class, EnumConverter>() {
    @NotNull
    protected EnumConverter create(final Class key) {
      return new EnumConverter(key);
    }
  };
  private final Class<T> myType;

  private EnumConverter(final Class<T> aClass) {
    myType = aClass;
  }

  public static <T extends Enum> EnumConverter<T>  createEnumConverter(Class<T> aClass) {
    return ourCache.get(aClass);
  }

  private String getStringValue(final T anEnum) {
    return NamedEnumUtil.getEnumValueByElement(anEnum);
  }

  public final T fromString(final String s, final ConvertContext context) {
    return s==null?null:(T)NamedEnumUtil.getEnumElementByValue((Class)myType, s);
  }

  public final String toString(final T t, final ConvertContext context) {
    return t == null? null:getStringValue(t);
  }

  public String getErrorMessage(@Nullable final String s, final ConvertContext context) {
    return CodeInsightBundle.message("error.unknown.enum.value.message", s);
  }

  @NotNull
  public Collection<? extends T> getVariants(final ConvertContext context) {
    final XmlElement element = context.getXmlElement();
    if (element instanceof XmlTag) {
      final XmlTag simpleContent = XmlUtil.getSchemaSimpleContent((XmlTag)element);
      if (simpleContent != null && XmlUtil.collectEnumerationValues(simpleContent, new HashSet<String>())) {
        return Collections.emptyList();
      }
    }
    return Arrays.asList(myType.getEnumConstants());
  }

  public boolean isExhaustive() {
    return !ReflectionCache.isAssignable(NonExhaustiveEnum.class, myType);
  }
}
