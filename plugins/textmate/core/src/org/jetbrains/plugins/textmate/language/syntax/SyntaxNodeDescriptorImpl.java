package org.jetbrains.plugins.textmate.language.syntax;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.CollectionFactory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil;

import java.util.*;

class SyntaxNodeDescriptorImpl implements MutableSyntaxNodeDescriptor {
  private static final Logger LOG = Logger.getInstance(SyntaxNodeDescriptor.class);

  private Int2ObjectMap<SyntaxNodeDescriptor> myRepository = new Int2ObjectOpenHashMap<>();
  private Map<Constants.StringKey, CharSequence> myStringAttributes = new EnumMap<>(Constants.StringKey.class);
  private Map<Constants.CaptureKey, Int2ObjectMap<CharSequence>> myCaptures = new EnumMap<>(Constants.CaptureKey.class);

  private List<SyntaxNodeDescriptor> myChildren = new ArrayList<>();
  private List<InjectionNodeDescriptor> myInjections = new ArrayList<>();

  private final SyntaxNodeDescriptor myParentNode;
  private CharSequence myScopeName = null;

  SyntaxNodeDescriptorImpl(@Nullable SyntaxNodeDescriptor parentNode) {
    myParentNode = parentNode;
  }

  @Override
  public void setStringAttribute(@NotNull Constants.StringKey key, @Nullable CharSequence value) {
    myStringAttributes.put(key, value);
  }

  @Nullable
  @Override
  public CharSequence getStringAttribute(@NotNull Constants.StringKey key) {
    return myStringAttributes.get(key);
  }

  @Override
  public void setCaptures(@NotNull Constants.CaptureKey key, @Nullable Int2ObjectMap<CharSequence> captures) {
    myCaptures.put(key, captures);
  }

  @Nullable
  @Override
  public Int2ObjectMap<CharSequence> getCaptures(@NotNull Constants.CaptureKey key) {
    return myCaptures.get(key);
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
  public void setScopeName(@NotNull CharSequence scopeName) {
    myScopeName = scopeName;
  }

  @Override
  public void compact() {
    myStringAttributes = PreferencesReadUtil.compactMap(myStringAttributes);
    myCaptures = PreferencesReadUtil.compactMap(myCaptures);
    myChildren = compactList(myChildren);
    myInjections = compactList(myInjections);
    myRepository = compactMap(myRepository);
  }

  private static Int2ObjectMap<SyntaxNodeDescriptor> compactMap(Int2ObjectMap<SyntaxNodeDescriptor> map) {
    if (map.isEmpty()) {
      return null;
    }
    CollectionFactory.trimMap(map);
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
  public CharSequence getScopeName() {
    return myScopeName;
  }

  @Nullable
  @Override
  public SyntaxNodeDescriptor getParentNode() {
    return myParentNode;
  }

  @Override
  public String toString() {
    CharSequence name = myStringAttributes.get(Constants.StringKey.NAME);
    return name != null ? "Syntax rule: " + name : super.toString();
  }
}
