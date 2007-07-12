package com.intellij.lang.properties.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel;
import com.intellij.ide.util.treeView.smartTree.*;
import com.intellij.lang.properties.editor.PropertiesGroupingStructureViewModel;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.Comparator;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 10, 2005
 * Time: 3:13:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesFileStructureViewModel extends TextEditorBasedStructureViewModel implements PropertiesGroupingStructureViewModel {
  private PropertiesFile myPropertiesFile;
  private final GroupByWordPrefixes myGroupByWordPrefixes;
  @NonNls public static final String KIND_SORTER_ID = "KIND_SORTER";
  private static final Sorter KIND_SORTER = new Sorter() {
    public Comparator getComparator() {
      return new Comparator() {
        public int compare(final Object o1, final Object o2) {
          int weight1 = o1 instanceof PropertiesPrefixGroup ? 1 : 0;
          int weight2 = o2 instanceof PropertiesPrefixGroup ? 1 : 0;
          return weight1 - weight2;
        }
      };
    }

    @NotNull
    public ActionPresentation getPresentation() {
      String name = "Sort by kind";
      return new ActionPresentationData(name, name, null);
    }

    @NotNull
    public String getName() {
      return KIND_SORTER_ID;
    }
  };

  public PropertiesFileStructureViewModel(final PropertiesFile root) {
    super(root);
    myPropertiesFile = root;
    String separator = PropertiesSeparatorManager.getInstance().getSeparator(root.getProject(), root.getVirtualFile());
    myGroupByWordPrefixes = new GroupByWordPrefixes(separator);
  }

  public void setSeparator(String separator) {
    myGroupByWordPrefixes.setSeparator(separator);
    PropertiesSeparatorManager.getInstance().setSeparator(myPropertiesFile.getVirtualFile(), separator);
  }

  public String getSeparator() {
    return myGroupByWordPrefixes.getSeparator();
  }

  @NotNull
  public StructureViewTreeElement getRoot() {
    return new PropertiesFileStructureViewElement(myPropertiesFile);
  }

  @NotNull
  public Grouper[] getGroupers() {
    return new Grouper[]{myGroupByWordPrefixes};
  }

  @NotNull
  public Sorter[] getSorters() {
    return new Sorter[] {Sorter.ALPHA_SORTER, KIND_SORTER};
  }

  @NotNull
  public Filter[] getFilters() {
    return Filter.EMPTY_ARRAY;
  }

  protected PsiFile getPsiFile() {
    return myPropertiesFile;
  }

  @NotNull
  protected Class[] getSuitableClasses() {
    return new Class[] {Property.class};
  }
}
