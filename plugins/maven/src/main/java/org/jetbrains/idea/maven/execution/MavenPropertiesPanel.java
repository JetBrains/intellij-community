// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.AddEditRemovePanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenConfigurableBundle;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MavenPropertiesPanel extends AddEditRemovePanel<Pair<String, String>> {
  private final Map<String, String> myAvailableProperties;

  public MavenPropertiesPanel(Map<String, String> availableProperties) {
    super(new MyPropertiesTableModel(), new ArrayList<>(), null);
    setPreferredSize(new Dimension(100, 100));
    myAvailableProperties = availableProperties;
  }

  @Override
  protected Pair<String, String> addItem() {
    return doAddOrEdit(null);
  }

  @Override
  protected boolean removeItem(Pair<String, String> o) {
    return true;
  }

  @Override
  protected Pair<String, String> editItem(@NotNull Pair<String, String> o) {
    return doAddOrEdit(o);
  }

  private @Nullable Pair<String, String> doAddOrEdit(@Nullable Pair<String, String> o) {
    EditMavenPropertyDialog d = new EditMavenPropertyDialog(o, myAvailableProperties);
    if (!d.showAndGet()) {
      return null;
    }
    return d.getValue();
  }

  public Map<String, String> getDataAsMap() {
    Map<String, String> result = new LinkedHashMap<>();
    for (Pair<String, String> p : getData()) {
      result.put(p.getFirst(), p.getSecond());
    }
    return result;
  }

  public void setDataFromMap(Map<String, String> map) {
    List<Pair<String, String>> result = new ArrayList<>();
    for (Map.Entry<String, String> e : map.entrySet()) {
      result.add(Pair.create(e.getKey(), e.getValue()));
    }
    setData(result);
  }

  private static class MyPropertiesTableModel extends AddEditRemovePanel.TableModel<Pair<String, String>> {
    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public @NlsContexts.ColumnName String getColumnName(int c) {
      return c == 0 ? MavenConfigurableBundle.message("column.name.name") : MavenConfigurableBundle.message("column.name.value");
    }

    @Override
    public Object getField(Pair<String, String> o, int c) {
      return c == 0 ? o.getFirst() : o.getSecond();
    }
  }

}
