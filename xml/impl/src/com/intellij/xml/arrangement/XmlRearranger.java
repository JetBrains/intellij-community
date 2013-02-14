package com.intellij.xml.arrangement;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.arrangement.ArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.psi.codeStyle.arrangement.StdArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsRepresentationAware;
import com.intellij.psi.codeStyle.arrangement.settings.DefaultArrangementSettingsRepresentationManager;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class XmlRearranger
  implements Rearranger<XmlElementArrangementEntry>,
             ArrangementStandardSettingsAware,
             ArrangementStandardSettingsRepresentationAware {

  private static final Set<ArrangementEntryType> SUPPORTED_TYPES = EnumSet.of(
    ArrangementEntryType.XML_ATTRIBUTE, ArrangementEntryType.XML_TAG);

  private static final List<StdArrangementMatchRule> DEFAULT_MATCH_RULES = new ArrayList<StdArrangementMatchRule>();

  private static final StdArrangementSettings DEFAULT_SETTINGS = new StdArrangementSettings(
    Collections.<ArrangementGroupingRule>emptyList(), DEFAULT_MATCH_RULES);

  @NotNull private static final TObjectIntHashMap<Object> WEIGHTS = new TObjectIntHashMap<Object>();

  @NotNull
  private static final Comparator<Object> COMPARATOR = new Comparator<Object>() {
    @Override
    public int compare(Object o1, Object o2) {
      if (WEIGHTS.containsKey(o1) && WEIGHTS.containsKey(o2)) {
        return WEIGHTS.get(o1) - WEIGHTS.get(o2);
      }
      else if (WEIGHTS.containsKey(o1) && !WEIGHTS.containsKey(o2)) {
        return -1;
      }
      else if (!WEIGHTS.containsKey(o1) && WEIGHTS.containsKey(o2)) {
        return 1;
      }
      else {
        return o1.hashCode() - o2.hashCode();
      }
    }
  };

  static {
    final Object[] ids = {
      ArrangementEntryType.XML_TAG, ArrangementEntryType.XML_ATTRIBUTE
    };
    for (int i = 0; i < ids.length; i++) {
      WEIGHTS.put(ids[i], i);
    }
  }

  @Nullable
  @Override
  public StdArrangementSettings getDefaultSettings() {
    return DEFAULT_SETTINGS;
  }

  @Override
  public boolean isNameFilterSupported() {
    return true;
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementEntryType type, @Nullable ArrangementMatchCondition current) {
    return SUPPORTED_TYPES.contains(type);
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementModifier modifier, @Nullable ArrangementMatchCondition current) {
    return false;
  }

  @Override
  public boolean isEnabled(@NotNull ArrangementGroupingType groupingType, @Nullable ArrangementEntryOrderType orderType) {
    return false;
  }

  @NotNull
  @Override
  public Collection<Set<?>> getMutexes() {
    return Collections.<Set<?>>singleton(SUPPORTED_TYPES);
  }

  @Nullable
  @Override
  public Pair<XmlElementArrangementEntry, List<XmlElementArrangementEntry>> parseWithNew(@NotNull PsiElement root,
                                                                                         @Nullable Document document,
                                                                                         @NotNull Collection<TextRange> ranges,
                                                                                         @NotNull PsiElement element,
                                                                                         @Nullable ArrangementSettings settings) {
    final XmlArrangementParseInfo newEntryInfo = new XmlArrangementParseInfo();
    element.accept(new XmlArrangementVisitor(newEntryInfo, Collections.singleton(element.getTextRange())));

    if (newEntryInfo.getEntries().size() != 1) {
      return null;
    }
    final XmlElementArrangementEntry entry = newEntryInfo.getEntries().get(0);
    final XmlArrangementParseInfo existingEntriesInfo = new XmlArrangementParseInfo();
    root.accept(new XmlArrangementVisitor(existingEntriesInfo, ranges));
    return Pair.create(entry, existingEntriesInfo.getEntries());
  }

  @NotNull
  @Override
  public List<XmlElementArrangementEntry> parse(@NotNull PsiElement root,
                                                @Nullable Document document,
                                                @NotNull Collection<TextRange> ranges,
                                                @Nullable ArrangementSettings settings) {
    final XmlArrangementParseInfo parseInfo = new XmlArrangementParseInfo();
    root.accept(new XmlArrangementVisitor(parseInfo, ranges));
    return parseInfo.getEntries();
  }

  @Override
  public int getBlankLines(@NotNull CodeStyleSettings settings,
                           @Nullable XmlElementArrangementEntry parent,
                           @Nullable XmlElementArrangementEntry previous,
                           @NotNull XmlElementArrangementEntry target) {
    return -1;
  }

  @NotNull
  @Override
  public String getDisplayValue(@NotNull ArrangementEntryType type) {
    switch (type) {
      case XML_TAG:
        return "tag";
      case XML_ATTRIBUTE:
        return "attribute";
      default:
        return DefaultArrangementSettingsRepresentationManager.
          INSTANCE.getDisplayValue(type);
    }
  }

  @NotNull
  @Override
  public String getDisplayValue(@NotNull ArrangementModifier modifier) {
    return DefaultArrangementSettingsRepresentationManager.INSTANCE.getDisplayValue(modifier);
  }

  @NotNull
  @Override
  public String getDisplayValue(@NotNull ArrangementGroupingType groupingType) {
    return DefaultArrangementSettingsRepresentationManager.INSTANCE.getDisplayValue(groupingType);
  }

  @NotNull
  @Override
  public String getDisplayValue(@NotNull ArrangementEntryOrderType orderType) {
    return DefaultArrangementSettingsRepresentationManager.INSTANCE.getDisplayValue(orderType);
  }

  @NotNull
  @Override
  public <T> List<T> sort(@NotNull Collection<T> ids) {
    final List<T> result = new ArrayList<T>(ids);
    Collections.sort(result, COMPARATOR);
    return result;
  }
}
