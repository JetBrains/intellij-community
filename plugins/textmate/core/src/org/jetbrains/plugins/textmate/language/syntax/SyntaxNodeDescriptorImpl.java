package org.jetbrains.plugins.textmate.language.syntax;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

final class SyntaxNodeDescriptorImpl implements MutableSyntaxNodeDescriptor {
  private static final Logger LOG = LoggerFactory.getLogger(SyntaxNodeDescriptorImpl.class);

  private Int2ObjectMap<SyntaxNodeDescriptor> myRepository = new Int2ObjectOpenHashMap<>();
  private Map<Constants.StringKey, CharSequence> myStringAttributes = new EnumMap<>(Constants.StringKey.class);
  private Map<Constants.CaptureKey, TextMateCapture[]> myCaptures = new EnumMap<>(Constants.CaptureKey.class);

  private List<SyntaxNodeDescriptor> myChildren = new ArrayList<>();
  private List<InjectionNodeDescriptor> myInjections = new ArrayList<>();

  private final SyntaxNodeDescriptor myParentNode;

  private final @Nullable CharSequence myScopeName;

  SyntaxNodeDescriptorImpl(@Nullable CharSequence scopeName, @Nullable SyntaxNodeDescriptor parentNode) {
    myParentNode = parentNode;
    myScopeName = scopeName;
  }

  @Override
  public void setStringAttribute(@NotNull Constants.StringKey key, @Nullable CharSequence value) {
    myStringAttributes.put(key, value);
  }

  @Override
  public @Nullable CharSequence getStringAttribute(@NotNull Constants.StringKey key) {
    return myStringAttributes.get(key);
  }

  @Override
  public void setCaptures(@NotNull Constants.CaptureKey key, TextMateCapture @Nullable [] captures) {
    myCaptures.put(key, captures);
  }

  @Override
  public boolean hasBackReference(Constants.@NotNull StringKey key) {
    return true;
  }

  @Override
  public TextMateCapture[] getCaptureRules(Constants.@NotNull CaptureKey key) {
    return myCaptures.get(key);
  }

  @Override
  public boolean hasBackReference(Constants.@NotNull CaptureKey key, int group) {
    return true;
  }

  @Override
  public void addChild(SyntaxNodeDescriptor descriptor) {
    myChildren.add(descriptor);
  }

  @Override
  public @NotNull List<SyntaxNodeDescriptor> getChildren() {
    return myChildren;
  }

  @Override
  public void appendRepository(int ruleId, SyntaxNodeDescriptor descriptor) {
    myRepository.put(ruleId, descriptor);
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
    if (map instanceof Int2ObjectOpenHashMap) {
      ((Int2ObjectOpenHashMap<SyntaxNodeDescriptor>)map).trim();
    }
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

  @Override
  public @NotNull List<InjectionNodeDescriptor> getInjections() {
    return myInjections;
  }

  @Override
  public void addInjection(@NotNull InjectionNodeDescriptor injection) {
    myInjections.add(injection);
  }

  @Override
  public @NotNull SyntaxNodeDescriptor findInRepository(int ruleId) {
    SyntaxNodeDescriptor syntaxNodeDescriptor = myRepository != null ? myRepository.get(ruleId) : null;
    if (syntaxNodeDescriptor == null && myParentNode != null) {
      return myParentNode.findInRepository(ruleId);
    }
    if (syntaxNodeDescriptor == null) {
      LOG.warn("Can't find repository {}", ruleId);
      return EMPTY_NODE;
    }
    return syntaxNodeDescriptor;
  }

  @Override
  public @Nullable CharSequence getScopeName() {
    return myScopeName;
  }

  @Override
  public @Nullable SyntaxNodeDescriptor getParentNode() {
    return myParentNode;
  }

  @Override
  public String toString() {
    CharSequence name = myStringAttributes.get(Constants.StringKey.NAME);
    return name != null ? "Syntax rule: " + name : super.toString();
  }
}
