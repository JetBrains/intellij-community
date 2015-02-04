package org.jetbrains.idea.svn.difftool.properties;

import com.intellij.diff.DiffContext;
import com.intellij.diff.FrameDiffTool.DiffViewer;
import com.intellij.diff.FrameDiffTool.ToolbarComponents;
import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.comparison.TrimUtil;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.fragments.LineFragmentImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.jetbrains.idea.svn.properties.PropertyValue;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.progress.DumbProgressIndicator.INSTANCE;

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

    PropertiesTableModel model = new PropertiesTableModel(titles[0], titles[1], this);
    myTable = new PropertiesTableView(model);
    myTable.getTableHeader().setReorderingAllowed(false);
    myTable.setIntercellSpacing(new Dimension(0, 1));
    myTable.setCellSelectionEnabled(false);
    myTable.setTableHeader(null);

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

    PropertiesTableModel model = (PropertiesTableModel)myTable.getModel();
    model.setItems(records);
    model.updateRowHeights(myTable);
  }

  @Nullable
  private static PropertyDiffRecord createRecord(@NotNull String name, @Nullable PropertyValue value1, @Nullable PropertyValue value2) {
    assert value1 != null || value2 != null;

    String text1 = value1 != null ? value1.toString() : null;
    String text2 = value2 != null ? value2.toString() : null;

    List<? extends LineFragment> fragments = compareValues(text1, text2);

    return new PropertyDiffRecord(name, text1, text2, fragments);
  }

  @Nullable
  private static List<PropertyData> getProperties(@NotNull DiffContent content) {
    if (content instanceof SvnPropertiesDiffRequest.PropertyContent) {
      return ((SvnPropertiesDiffRequest.PropertyContent)content).getProperties();
    }
    return null;
  }

  @NotNull
  private static List<? extends LineFragment> compareValues(@Nullable CharSequence text1, @Nullable CharSequence text2) {
    assert text1 != null || text2 != null;
    if (text1 == null) {
      return createEverythingChanged(0, text2.length(), 0, StringUtil.countNewLines(text2) + 1);
    }
    if (text2 == null) {
      return createEverythingChanged(text1.length(), 0, StringUtil.countNewLines(text1) + 1, 0);
    }

    // we calc diff it in EDT without progress, so fair comparing of MB properties is not acceptable
    // TODO: we can count diff in in background
    int MAX_ITEM_COUNT = 10000;

    Couple<Integer> couple1 = countWordsAndLines(text1);
    Couple<Integer> couple2 = countWordsAndLines(text1);
    int words1 = couple1.first;
    int words2 = couple2.first;
    int lines1 = couple1.second;
    int lines2 = couple2.second;

    try {
      ComparisonManager comparisonManager = ComparisonManager.getInstance();
      if (words1 < MAX_ITEM_COUNT && words2 < MAX_ITEM_COUNT) {
        return comparisonManager.squash(comparisonManager.compareLinesInner(text1, text2, ComparisonPolicy.DEFAULT, INSTANCE));
      }
      if (lines1 < MAX_ITEM_COUNT && lines2 < MAX_ITEM_COUNT) {
        return comparisonManager.squash(comparisonManager.compareLines(text1, text2, ComparisonPolicy.DEFAULT, INSTANCE));
      }
    }
    catch (DiffTooBigException e) {
      LOG.warn(e);
    }

    return createEverythingChanged(text1.length(), text2.length(), lines1, lines2);
  }

  @NotNull
  private static List<? extends LineFragment> createEverythingChanged(int length1, int length2, int lines1, int lines2) {
    return Collections.singletonList(new LineFragmentImpl(0, lines1, 0, lines2, 0, length1, 0, length2));
  }

  @NotNull
  private static Couple<Integer> countWordsAndLines(@NotNull CharSequence text) {
    int words = 0;
    int lines = 1;

    boolean inWord = false;

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '\n') lines++;
      if (!inWord && TrimUtil.isAlpha(c)) {
        words++;
        inWord = true;
      }
      else {
        inWord = false;
      }
    }

    return Couple.of(words, lines);
  }

  @NotNull
  private List<AnAction> createToolbar() {
    return new ArrayList<AnAction>();
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
    @Nullable private final String myBefore;
    @Nullable private final String myAfter;
    @NotNull private final List<? extends LineFragment> myFragments;

    public PropertyDiffRecord(@NotNull String name,
                              @Nullable String before,
                              @Nullable String after,
                              @NotNull List<? extends LineFragment> fragments) {
      assert before != null || after != null;

      // TODO: show differences in line separators ?
      if (before != null) before = StringUtil.convertLineSeparators(before);
      if (after != null) after = StringUtil.convertLineSeparators(after);

      myName = name;
      myBefore = before;
      myAfter = after;
      myFragments = fragments;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @Nullable
    public String getBefore() {
      return myBefore;
    }

    @Nullable
    public String getAfter() {
      return myAfter;
    }

    @NotNull
    public List<? extends LineFragment> getFragments() {
      return myFragments;
    }
  }
}
