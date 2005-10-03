package com.intellij.structuralsearch.plugin.replace;

import com.intellij.structuralsearch.MatchOptions;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.Key;
import org.jdom.Element;
import org.jdom.DataConversionException;
import org.jdom.Attribute;
import org.jetbrains.annotations.NonNls;
import gnu.trove.THashMap;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 5, 2004
 * Time: 7:51:38 PM
 * To change this template use File | Settings | File Templates.
 */
public class ReplaceOptions implements JDOMExternalizable, Cloneable, UserDataHolder {
  private String replacement = "";
  private boolean toShortenFQN;
  private boolean myToReformatAccordingToStyle;
  private MatchOptions matchOptions = new MatchOptions();

  @NonNls private static final String REFORMAT_ATTR_NAME = "reformatAccordingToStyle";
  @NonNls private static final String REPLACEMENT_ATTR_NAME = "replacement";
  @NonNls private static final String SHORTEN_FQN_ATTR_NAME = "shortenFQN";

  private THashMap myUserMap = null;

  public String getReplacement() {
    return replacement;
  }

  public void setReplacement(String replacement) {
    this.replacement = replacement;
  }

  public boolean isToShortenFQN() {
    return toShortenFQN;
  }

  public void setToShortenFQN(boolean shortedFQN) {
    this.toShortenFQN = shortedFQN;
  }

  public boolean isToReformatAccordingToStyle() {
    return myToReformatAccordingToStyle;
  }

  public MatchOptions getMatchOptions() {
    return matchOptions;
  }

  public void setMatchOptions(MatchOptions matchOptions) {
    this.matchOptions = matchOptions;
  }

  public void setToReformatAccordingToStyle(boolean reformatAccordingToStyle) {
    myToReformatAccordingToStyle = reformatAccordingToStyle;
  }

  public void readExternal(Element element) {
    matchOptions.readExternal(element);

    Attribute attribute = element.getAttribute(REFORMAT_ATTR_NAME);
    try {
      myToReformatAccordingToStyle = attribute.getBooleanValue();
    } catch(DataConversionException ex) {
    }

    attribute = element.getAttribute(SHORTEN_FQN_ATTR_NAME);
    try {
      toShortenFQN = attribute.getBooleanValue();
    } catch(DataConversionException ex) {}
    
    replacement = element.getAttributeValue(REPLACEMENT_ATTR_NAME);
  }

  public void writeExternal(Element element) {
    matchOptions.writeExternal(element);

    element.setAttribute(REFORMAT_ATTR_NAME,String.valueOf(myToReformatAccordingToStyle));
    element.setAttribute(SHORTEN_FQN_ATTR_NAME,String.valueOf(toShortenFQN));
    element.setAttribute(REPLACEMENT_ATTR_NAME,replacement);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ReplaceOptions)) return false;

    final ReplaceOptions replaceOptions = (ReplaceOptions)o;

    if (myToReformatAccordingToStyle != replaceOptions.myToReformatAccordingToStyle) return false;
    if (toShortenFQN != replaceOptions.toShortenFQN) return false;
    if (matchOptions != null ? !matchOptions.equals(replaceOptions.matchOptions) : replaceOptions.matchOptions != null) return false;
    if (replacement != null ? !replacement.equals(replaceOptions.replacement) : replaceOptions.replacement != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (replacement != null ? replacement.hashCode() : 0);
    result = 29 * result + (toShortenFQN ? 1 : 0);
    result = 29 * result + (myToReformatAccordingToStyle ? 1 : 0);
    result = 29 * result + (matchOptions != null ? matchOptions.hashCode() : 0);
    return result;
  }

  public ReplaceOptions clone() {
    try {
      ReplaceOptions replaceOptions = (ReplaceOptions) super.clone();
      replaceOptions.matchOptions = matchOptions.clone();
      return replaceOptions;
    } catch (CloneNotSupportedException e) {
      e.printStackTrace();
      return null;
    }
  }

  public <T> T getUserData(Key<T> key) {
    if (myUserMap==null) return null;
    return (T)myUserMap.get(key);
  }

  public <T> void putUserData(Key<T> key, T value) {
    if (myUserMap==null) myUserMap = new THashMap(1);
    myUserMap.put(key,value);
  }
}
