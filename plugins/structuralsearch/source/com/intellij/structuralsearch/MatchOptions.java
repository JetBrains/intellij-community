package com.intellij.structuralsearch;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.SearchScope;
import org.jdom.Attribute;
import org.jdom.DataConversionException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * match options
 */
public class MatchOptions implements JDOMExternalizable, Cloneable {
  @NonNls private static final String TEXT_ATTRIBUTE_NAME = "text";

  private boolean looseMatching;
  private boolean distinct;
  private boolean recursiveSearch;
  private boolean caseSensitiveMatch;
  private boolean resultIsContextMatch = false;
  private FileType myFileType = StructuralSearchUtil.DEFAULT_FILE_TYPE;
  private Language myDialect = null;
  private int maxMatches = DEFAULT_MAX_MATCHES_COUNT;
  public static final int DEFAULT_MAX_MATCHES_COUNT = 1000;

  private SearchScope scope;
  private SearchScope downUpMatchScope;
  private String searchCriteria = "";
  private Map<String,MatchVariableConstraint> variableConstraints;

  private String myPatternContext;

  @NonNls private static final String DISTINCT_ATTRIBUTE_NAME = "distinct";
  @NonNls private static final String RECURSIVE_ATTRIBUTE_NAME = "recursive";
  @NonNls private static final String CASESENSITIVE_ATTRIBUTE_NAME = "caseInsensitive";
  @NonNls private static final String MAXMATCHES_ATTRIBUTE_NAME = "maxMatches";
  //private static final String SCOPE_ATTRIBUTE_NAME = "scope";
  @NonNls private static final String CONSTRAINT_TAG_NAME = "constraint";
  @NonNls private static final String FILE_TYPE_ATTR_NAME = "type";
  @NonNls private static final String DIALECT_ATTR_NAME = "dialect";
  @NonNls public static final String INSTANCE_MODIFIER_NAME = "Instance";
  @NonNls public static final String MODIFIER_ANNOTATION_NAME = "Modifier";
  @NonNls public static final String PACKAGE_LOCAL_MODIFIER_NAME = PsiModifier.PACKAGE_LOCAL;

  //private static final String UNDEFINED_SCOPE = "undefined";

  public void addVariableConstraint(MatchVariableConstraint constraint) {
    if (variableConstraints==null) {
      variableConstraints = new LinkedHashMap<String,MatchVariableConstraint>();
    }
    variableConstraints.put( constraint.getName(), constraint );
  }

  public boolean hasVariableConstraints() {
    return variableConstraints!=null;
  }

  public void clearVariableConstraints() {
    variableConstraints=null;
  }

  public MatchVariableConstraint getVariableConstraint(String name) {
    if (variableConstraints!=null) {
      return variableConstraints.get(name);
    }
    return null;
  }

  public Iterator<String> getVariableConstraintNames() {
    if (variableConstraints==null) return null;
    return variableConstraints.keySet().iterator();
  }

  public void setCaseSensitiveMatch(boolean caseSensitiveMatch) {
    this.caseSensitiveMatch = caseSensitiveMatch;
  }

  public boolean isCaseSensitiveMatch() {
    return caseSensitiveMatch;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuffer result = new StringBuffer();

    result.append("match options:\n");
    result.append("search pattern:\n");
    result.append(searchCriteria);
    result.append("\nsearch scope:\n");

    // @TODO print scope
    //result.append((scopeHandler!=null)?scopeHandler.toString():"undefined scope");

    result.append("\nrecursive:");
    result.append(recursiveSearch);

    result.append("\ndistinct:");
    result.append(distinct);

    result.append("\ncasesensitive:");
    result.append(caseSensitiveMatch);

    return result.toString();
  }

  public boolean isDistinct() {
    return distinct;
  }

  public void setDistinct(boolean distinct) {
    this.distinct = distinct;
  }

  public boolean isRecursiveSearch() {
    return recursiveSearch;
  }

  public void setRecursiveSearch(boolean recursiveSearch) {
    this.recursiveSearch = recursiveSearch;
  }

  public boolean isLooseMatching() {
    return looseMatching;
  }

  public void setLooseMatching(boolean looseMatching) {
    this.looseMatching = looseMatching;
  }

  public void setSearchPattern(String text) {
    searchCriteria = text;
  }

  public String getSearchPattern() {
    return searchCriteria;
  }

  public void setMaxMatchesCount(int _maxMatches) {
    maxMatches = _maxMatches;
  }

  public int getMaxMatchesCount() {
    return maxMatches;
  }

  public boolean isResultIsContextMatch() {
    return resultIsContextMatch;
  }

  public void setResultIsContextMatch(boolean resultIsContextMatch) {
    this.resultIsContextMatch = resultIsContextMatch;
  }

  public SearchScope getScope() {
    return scope;
  }

  public void setScope(SearchScope scope) {
    this.scope = scope;
  }

  public SearchScope getDownUpMatchScope() {
    return downUpMatchScope;
  }

  public void setDownUpMatchScope(final SearchScope downUpMatchScope) {
    this.downUpMatchScope = downUpMatchScope;
  }

  public void writeExternal(Element element) {
    element.setAttribute(TEXT_ATTRIBUTE_NAME,getSearchPattern());
    element.setAttribute(RECURSIVE_ATTRIBUTE_NAME,String.valueOf(recursiveSearch));
    if (distinct) element.setAttribute(DISTINCT_ATTRIBUTE_NAME,String.valueOf(distinct));
    element.setAttribute(CASESENSITIVE_ATTRIBUTE_NAME,String.valueOf(caseSensitiveMatch));

    //@TODO serialize scope!

    //if (myFileType != StdFileTypes.JAVA) {
      element.setAttribute(FILE_TYPE_ATTR_NAME, myFileType.getName());
    //}

    if (myDialect != null) {
      element.setAttribute(DIALECT_ATTR_NAME, myDialect.getID());
    }

    if (variableConstraints!=null) {
      for (final MatchVariableConstraint matchVariableConstraint : variableConstraints.values()) {
        final Element infoElement = new Element(CONSTRAINT_TAG_NAME);
        element.addContent(infoElement);
        matchVariableConstraint.writeExternal(infoElement);
      }
    }
  }

  public void readExternal(Element element) {
    setSearchPattern(element.getAttribute(TEXT_ATTRIBUTE_NAME).getValue());

    Attribute attr = element.getAttribute(RECURSIVE_ATTRIBUTE_NAME);
    if (attr!=null) {
      try {
        recursiveSearch = attr.getBooleanValue();
      } catch(DataConversionException ignored) {}
    }

    attr = element.getAttribute(DISTINCT_ATTRIBUTE_NAME);
    if (attr!=null) {
      try {
        distinct = attr.getBooleanValue();
      } catch(DataConversionException ignored) {}
    }

    attr = element.getAttribute(CASESENSITIVE_ATTRIBUTE_NAME);
    if (attr!=null) {
      try {
        caseSensitiveMatch = attr.getBooleanValue();
      } catch(DataConversionException ignored) {}
    }

    attr = element.getAttribute(MAXMATCHES_ATTRIBUTE_NAME);
    if (attr!=null) {
      try {
        maxMatches = attr.getIntValue();
      } catch(DataConversionException ignored) {}
    }

    attr = element.getAttribute(FILE_TYPE_ATTR_NAME);
    if (attr!=null) {
      String value = attr.getValue();
      myFileType = getFileTypeByName(value);
    }

    attr = element.getAttribute(DIALECT_ATTR_NAME);
    if (attr != null) {
      myDialect = Language.findLanguageByID(attr.getValue());
    }

    // @TODO deserialize scope

    List elements = element.getChildren(CONSTRAINT_TAG_NAME);
    if (elements!=null && !elements.isEmpty()) {
      for (final Object element1 : elements) {
        final MatchVariableConstraint constraint = new MatchVariableConstraint();
        constraint.readExternal((Element)element1);
        addVariableConstraint(constraint);
      }
    }
  }

  private static FileType getFileTypeByName(String value) {
    if (value != null) {
      for (FileType type : StructuralSearchUtil.getSuitableFileTypes()) {
        if (value.equals(type.getName())) {
          return type;
        }
      }
    }

    return StructuralSearchUtil.DEFAULT_FILE_TYPE;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MatchOptions)) return false;

    final MatchOptions matchOptions = (MatchOptions)o;

    if (caseSensitiveMatch != matchOptions.caseSensitiveMatch) return false;
    if (distinct != matchOptions.distinct) return false;
    //if (enableAutoIdentifySearchTarget != matchOptions.enableAutoIdentifySearchTarget) return false;
    if (looseMatching != matchOptions.looseMatching) return false;
    if (maxMatches != matchOptions.maxMatches) return false;
    if (recursiveSearch != matchOptions.recursiveSearch) return false;
    // @TODO support scope

    if (searchCriteria != null ? !searchCriteria.equals(matchOptions.searchCriteria) : matchOptions.searchCriteria != null) return false;
    if (variableConstraints != null ? !variableConstraints.equals(matchOptions.variableConstraints) : matchOptions.variableConstraints !=
                                                                                                      null) {
      return false;
    }
    if (myFileType != matchOptions.myFileType) {
      return false;
    }

    if (myDialect != null ? !myDialect.equals(matchOptions.myDialect) : matchOptions.myDialect != null) {
      return false;
    }

    if (myPatternContext != null ? !myPatternContext.equals(matchOptions.myPatternContext) : matchOptions.myPatternContext != null) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    int result;
    result = (looseMatching ? 1 : 0);
    result = 29 * result + (distinct ? 1 : 0);
    result = 29 * result + (recursiveSearch ? 1 : 0);
    result = 29 * result + (caseSensitiveMatch ? 1 : 0);
    //result = 29 * result + (enableAutoIdentifySearchTarget ? 1 : 0);
    result = 29 * result + maxMatches;
    // @TODO support scope
    result = 29 * result + (searchCriteria != null ? searchCriteria.hashCode() : 0);
    result = 29 * result + (variableConstraints != null ? variableConstraints.hashCode() : 0);
    if (myFileType != null) result = 29 * result + myFileType.hashCode();
    if (myDialect != null) result = 29 * result + myDialect.hashCode();
    return result;
  }

  public void setFileType(FileType fileType) {
    myFileType = fileType;
  }

  public FileType getFileType() {
    return myFileType;
  }

  public Language getDialect() {
    return myDialect;
  }

  public void setDialect(Language dialect) {
    myDialect = dialect;
  }

  public String getPatternContext() {
    return myPatternContext;
  }

  public void setPatternContext(String patternContext) {
    myPatternContext = patternContext;
  }

  public MatchOptions clone() {
    try {
      return (MatchOptions) super.clone();
    } catch (CloneNotSupportedException e) {
      e.printStackTrace();
      return null;
    }
  }
}
