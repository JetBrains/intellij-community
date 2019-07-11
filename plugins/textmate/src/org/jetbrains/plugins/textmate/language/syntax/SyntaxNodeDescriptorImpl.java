package org.jetbrains.plugins.textmate.language.syntax;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.jetbrains.plugins.textmate.regex.RegexFacade;

import java.util.*;

class SyntaxNodeDescriptorImpl implements MutableSyntaxNodeDescriptor {
  private static final Logger LOG = Logger.getInstance(SyntaxNodeDescriptor.class);

  private Map<String, String> myStringAttributes = new HashMap<>();
  private Map<String, RegexFacade> myRegexAttributes = new HashMap<>();
  private Map<String, Plist> myPlistAttributes = new HashMap<>();
  private Map<String, SyntaxNodeDescriptor> myRepository = new HashMap<>();

  private List<SyntaxNodeDescriptor> myChildren = new ArrayList<>();
  private List<InjectionNodeDescriptor> myInjections = new ArrayList<>();

  private final SyntaxNodeDescriptor myParentNode;
  private String myScopeName = null;

  SyntaxNodeDescriptorImpl(@Nullable SyntaxNodeDescriptor parentNode) {
    myParentNode = parentNode;
  }

  @Override
  public void setStringAttribute(String key, String value) {
    myStringAttributes.put(key, value);
  }

  @Nullable
  @Override
  public String getStringAttribute(String key) {
    return myStringAttributes.get(key);
  }

  @Override
  public void setPlistAttribute(String key, Plist value) {
    myPlistAttributes.put(key, value);
  }

  @Nullable
  @Override
  public Plist getPlistAttribute(String key) {
    return myPlistAttributes.get(key);
  }

  @Override
  public void setRegexAttribute(String key, RegexFacade value) {
    myRegexAttributes.put(key, value);
  }

  @Nullable
  @Override
  public RegexFacade getRegexAttribute(String key) {
    return myRegexAttributes.get(key);
  }

  @Override
  public void addChild(SyntaxNodeDescriptor descriptor) {
    myChildren.add(descriptor);
  }

  @NotNull
  @Override
  public List<SyntaxNodeDescriptor> getChildren() {
    return myChildren;
  }

  @Override
  public void appendRepository(String key, SyntaxNodeDescriptor descriptor) {
    myRepository.put(key, descriptor);
  }

  @Override
  public void setScopeName(@NotNull String scopeName) {
    myScopeName = scopeName;
  }

  @Override
  public void compact() {
    myStringAttributes = compactMap(myStringAttributes);
    myRegexAttributes = compactMap(myRegexAttributes);
    myPlistAttributes = compactMap(myPlistAttributes);
    myRepository = compactMap(myRepository);
    myChildren = compactList(myChildren);
    myInjections = compactList(myInjections);
  }

  private static <T> List<T> compactList(List<T> list) {
    if (list.isEmpty()) {
      return Collections.emptyList();
    }
    if (list.size() == 1) {
      return Collections.singletonList(list.get(0));
    }
    if (list instanceof ArrayList) {
      ((ArrayList<T>)list).trimToSize();
    }
    return list;
  }

  private static <T> Map<String, T> compactMap(Map<String, T> map) {
    if (map.isEmpty()) {
      return Collections.emptyMap();
    }
    if (map.size() == 1) {
      Map.Entry<String, T> singleEntry = map.entrySet().iterator().next();
      return Collections.singletonMap(singleEntry.getKey(), singleEntry.getValue());
    }
    return new HashMap<String, T>(map.size()) {{
      putAll(map);
    }};
  }

  @NotNull
  @Override
  public List<InjectionNodeDescriptor> getInjections() {
    return myInjections;
  }

  @Override
  public void addInjection(@NotNull InjectionNodeDescriptor injection) {
    myInjections.add(injection);
  }

  @NotNull
  @Override
  public SyntaxNodeDescriptor findInRepository(String key) {
    SyntaxNodeDescriptor syntaxNodeDescriptor = myRepository.get(key);
    if (syntaxNodeDescriptor == null && myParentNode != null) {
      return myParentNode.findInRepository(key);
    }
    if (syntaxNodeDescriptor == null) {
      LOG.warn("Can't find repository '" + key + "'");
      return EMPTY_NODE;
    }
    return syntaxNodeDescriptor;
  }

  @NotNull
  @Override
  public String getScopeName() {
    return myScopeName;
  }

  @Nullable
  @Override
  public SyntaxNodeDescriptor getParentNode() {
    return myParentNode;
  }

  @Override
  public String toString() {
    String name = myStringAttributes.get(Constants.NAME_KEY);
    return name != null ? "Syntax rule: " + name : super.toString();
  }
}
