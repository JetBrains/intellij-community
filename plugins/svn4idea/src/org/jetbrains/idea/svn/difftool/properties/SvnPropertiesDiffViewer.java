package org.jetbrains.idea.svn.difftool.properties;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffContext;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffViewer;
import com.intellij.openapi.util.diff.api.FrameDiffTool.ToolbarComponents;
import com.intellij.openapi.util.diff.comparison.iterables.DiffIterable;
import com.intellij.openapi.util.diff.comparison.iterables.DiffIterableUtil;
import com.intellij.openapi.util.diff.contents.DiffContent;
import com.intellij.openapi.util.diff.fragments.DiffFragment;
import com.intellij.openapi.util.diff.util.DiffUtil;
import com.intellij.openapi.util.diff.util.TextDiffType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.difftool.properties.SvnPropertiesDiffViewer.PropertyDiffRecord.ColoredChunk;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.jetbrains.idea.svn.properties.PropertyValue;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class SvnPropertiesDiffViewer implements DiffViewer {
  private static final Logger LOG = Logger.getInstance(SvnPropertiesDiffViewer.class);

  @Nullable private final Project myProject;

  @NotNull private final DiffContext myContext;
  @NotNull private final SvnPropertiesDiffRequest myRequest;

  @Nullable private final List<PropertyData> myProperties1;
  @Nullable private final List<PropertyData> myProperties2;

  @NotNull private final JPanel myPanel;
  @NotNull private final TableView<PropertyDiffRecord> myTable;

  public SvnPropertiesDiffViewer(@NotNull DiffContext context, @NotNull SvnPropertiesDiffRequest request) {
    myProject = context.getProject();

    myContext = context;
    myRequest = request;

    String[] titles = request.getContentTitles();

    DiffContent[] contents = request.getContents();
    myProperties1 = getProperties(contents[0]);
    myProperties2 = getProperties(contents[1]);
    assert myProperties1 != null || myProperties2 != null;

    PropertiesTableModel model = new PropertiesTableModel(myProperties1 != null, myProperties2 != null, titles[0], titles[1]);
    myTable = new TableView<PropertyDiffRecord>(model);
    myTable.getTableHeader().setReorderingAllowed(false);

    myPanel = new JPanel(new BorderLayout());
    myPanel.add(ScrollPaneFactory.createScrollPane(myTable), BorderLayout.CENTER);
  }

  @NotNull
  @Override
  public ToolbarComponents init() {
    rediff();

    ToolbarComponents components = new ToolbarComponents();
    components.toolbarActions = createToolbar();
    return components;
  }

  @Override
  public void dispose() {
  }

  //
  // Diff
  //

  public void rediff() {
    assert myProperties1 != null || myProperties2 != null;

    Map<String, PropertyValue> before = new HashMap<String, PropertyValue>();
    Map<String, PropertyValue> after = new HashMap<String, PropertyValue>();
    if (myProperties1 != null) {
      for (PropertyData data : myProperties1) {
        before.put(data.getName(), data.getValue());
      }
    }
    if (myProperties2 != null) {
      for (PropertyData data : myProperties2) {
        after.put(data.getName(), data.getValue());
      }
    }

    List<PropertyDiffRecord> records = new ArrayList<PropertyDiffRecord>();
    for (String name : ContainerUtil.union(before.keySet(), after.keySet())) {
      records.add(createRecord(name, before.get(name), after.get(name)));
    }
    ContainerUtil.sort(records, new Comparator<PropertyDiffRecord>() {
      @Override
      public int compare(PropertyDiffRecord o1, PropertyDiffRecord o2) {
        return StringUtil.naturalCompare(o1.getName(), o2.getName());
      }
    });
    ((PropertiesTableModel)myTable.getModel()).setItems(records);
  }

  @Nullable
  private static PropertyDiffRecord createRecord(@NotNull String name, @Nullable PropertyValue value1, @Nullable PropertyValue value2) {
    assert value1 != null || value2 != null;
    if (value1 == null) {
      return new PropertyDiffRecord(name, null, Collections.singletonList(new ColoredChunk(value2.toString(), null)), true);
    }
    if (value2 == null) {
      return new PropertyDiffRecord(name, Collections.singletonList(new ColoredChunk(value1.toString(), null)), null, true);
    }

    String text1 = value1.toString();
    String text2 = value2.toString();

    List<DiffFragment> fragments = DiffUtil.compareWords(text1, text2, 1000);

    List<ColoredChunk> chunks1 = new ArrayList<ColoredChunk>();
    List<ColoredChunk> chunks2 = new ArrayList<ColoredChunk>();

    DiffIterable iterable = DiffIterableUtil.createFragments(fragments, text1.length(), text2.length());
    for (Pair<DiffIterableUtil.Range, Boolean> pair : DiffIterableUtil.iterateAll(iterable)) {
      DiffIterableUtil.Range range = pair.first;
      Boolean equals = pair.second;

      if (range.start1 == range.end1) {
        chunks2.add(new ColoredChunk(text2.substring(range.start2, range.end2), equals ? null : TextDiffType.INSERTED));
      }
      else if (range.start2 == range.end2) {
        chunks1.add(new ColoredChunk(text1.substring(range.start1, range.end1), equals ? null : TextDiffType.DELETED));
      }
      else {
        chunks1.add(new ColoredChunk(text1.substring(range.start1, range.end1), equals ? null : TextDiffType.MODIFIED));
        chunks2.add(new ColoredChunk(text2.substring(range.start2, range.end2), equals ? null : TextDiffType.MODIFIED));
      }
    }

    return new PropertyDiffRecord(name, chunks1, chunks2, !fragments.isEmpty());
  }

  @Nullable
  private static List<PropertyData> getProperties(@NotNull DiffContent content) {
    if (content instanceof SvnPropertiesDiffRequest.PropertyContent) {
      return ((SvnPropertiesDiffRequest.PropertyContent)content).getProperties();
    }
    return null;
  }

  @NotNull
  private List<AnAction> createToolbar() {
    List<AnAction> result = new ArrayList<AnAction>();
    return result;
  }

  //
  // Getters
  //

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTable;
  }

  public static class PropertyDiffRecord {
    @NotNull private final String myName;
    @Nullable private final List<ColoredChunk> myBefore;
    @Nullable private final List<ColoredChunk> myAfter;
    private final boolean myIsChanged;

    public PropertyDiffRecord(@NotNull String name,
                              @Nullable List<ColoredChunk> before,
                              @Nullable List<ColoredChunk> after,
                              boolean isChanged) {
      myName = name;
      myBefore = before;
      myAfter = after;
      myIsChanged = isChanged;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @Nullable
    public List<ColoredChunk> getBefore() {
      return myBefore;
    }

    @Nullable
    public List<ColoredChunk> getAfter() {
      return myAfter;
    }

    public boolean isChanged() {
      return myIsChanged;
    }

    public static class ColoredChunk {
      @NotNull private final String myText;
      @Nullable private final TextDiffType myType;

      public ColoredChunk(@NotNull String text, @Nullable TextDiffType type) {
        myText = text;
        myType = type;
      }

      @NotNull
      public String getText() {
        return myText;
      }

      @Nullable
      public TextDiffType getType() {
        return myType;
      }
    }
  }
}
