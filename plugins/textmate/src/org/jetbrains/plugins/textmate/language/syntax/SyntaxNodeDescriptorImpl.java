package org.jetbrains.plugins.textmate.language.syntax;

import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.regex.RegexFacade;

import java.util.*;

class SyntaxNodeDescriptorImpl implements MutableSyntaxNodeDescriptor {
  private static final Logger LOG = Logger.getInstance(SyntaxNodeDescriptor.class);

  private TIntObjectHashMap<SyntaxNodeDescriptor> myRepository = new TIntObjectHashMap<>();
  private Map<Constants.StringKey, String> myStringAttributes = new EnumMap<>(Constants.StringKey.class);
  private Map<String, RegexFacade> myRegexAttributes = new HashMap<>();
  private Map<Constants.CaptureKey, TIntObjectHashMap<String>> myCaptures = new EnumMap<>(Constants.CaptureKey.class);

  private List<SyntaxNodeDescriptor> myChildren = new ArrayList<>();
  private List<InjectionNodeDescriptor> myInjections = new ArrayList<>();

  private final SyntaxNodeDescriptor myParentNode;
  private String myScopeName = null;

  SyntaxNodeDescriptorImpl(@Nullable SyntaxNodeDescriptor parentNode) {
    myParentNode = parentNode;
  }

  @Override
  public void setStringAttribute(@NotNull Constants.StringKey key, String value) {
    myStringAttributes.put(key, value);
  }

  @Nullable
  @Override
  public String getStringAttribute(@NotNull Constants.StringKey key) {
    return myStringAttributes.get(key);
  }

  @Override
  public void setCaptures(@NotNull Constants.CaptureKey key, @Nullable TIntObjectHashMap<String> captures) {
    myCaptures.put(key, captures);
  }

  @Nullable
  @Override
  public TIntObjectHashMap<String> getCaptures(@NotNull Constants.CaptureKey key) {
    return myCaptures.get(key);
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
  public void appendRepository(int ruleId, SyntaxNodeDescriptor descriptor) {
    myRepository.put(ruleId, descriptor);
  }

  @Override
  public void setScopeName(@NotNull String scopeName) {
    myScopeName = scopeName;
  }

  @Override
  public void compact() {
    myStringAttributes = compactMap(myStringAttributes);
    myRegexAttributes = compactMap(myRegexAttributes);
    myCaptures = compactMap(myCaptures);
    myChildren = compactList(myChildren);
    myInjections = compactList(myInjections);
    myRepository = compactMap(myRepository);
  }

  private static TIntObjectHashMap<SyntaxNodeDescriptor> compactMap(TIntObjectHashMap<SyntaxNodeDescriptor> map) {
    if (map.isEmpty()) {
      return null;
    }
    map.trimToSize();
    return map;
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

  private static <K, V> Map<K, V> compactMap(Map<K, V> map) {
    if (map.isEmpty()) {
      return Collections.emptyMap();
    }
    if (map.size() == 1) {
      Map.Entry<K, V> singleEntry = map.entrySet().iterator().next();
      return Collections.singletonMap(singleEntry.getKey(), singleEntry.getValue());
    }
    if (!(map instanceof HashMap)) {
      return map;
    }
    HashMap<K, V> result = new HashMap<>(map.size(), 1.0f);
    result.putAll(map);
    return result;
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
  public SyntaxNodeDescriptor findInRepository(int ruleId) {
    SyntaxNodeDescriptor syntaxNodeDescriptor = myRepository != null ? myRepository.get(ruleId) : null;
    if (syntaxNodeDescriptor == null && myParentNode != null) {
      return myParentNode.findInRepository(ruleId);
    }
    if (syntaxNodeDescriptor == null) {
      LOG.warn("Can't find repository " + ruleId);
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
    String name = myStringAttributes.get(Constants.StringKey.NAME);
    return name != null ? "Syntax rule: " + name : super.toString();
  }
}
