package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.indexing.FileBasedIndex;
import de.plushnikov.intellij.plugin.language.LombokConfigFileType;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigFile;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class LombokConfigUtil {

  private static final LombokConfigProperty[] EMPTY_LOMBOK_CONFIG_PROPERTIES = new LombokConfigProperty[0];

  public static List<LombokConfigProperty> findProperties(Project project, String key) {
    List<LombokConfigProperty> result = null;
    Collection<VirtualFile> virtualFiles = FileBasedIndex.getInstance().getContainingFiles(FileTypeIndex.NAME, LombokConfigFileType.INSTANCE,
        GlobalSearchScope.allScope(project));
    for (VirtualFile virtualFile : virtualFiles) {
      LombokConfigFile lombokConfigFile = (LombokConfigFile) PsiManager.getInstance(project).findFile(virtualFile);
      LombokConfigProperty[] properties = getLombokConfigProperties(lombokConfigFile);
      for (LombokConfigProperty property : properties) {
        if (key.equals(property.getKey())) {
          if (result == null) {
            result = new ArrayList<LombokConfigProperty>();
          }
          result.add(property);
        }
      }
    }
    return result != null ? result : Collections.<LombokConfigProperty>emptyList();
  }

  public static List<LombokConfigProperty> findProperties(Project project) {
    List<LombokConfigProperty> result = new ArrayList<LombokConfigProperty>();
    Collection<VirtualFile> virtualFiles = FileBasedIndex.getInstance().getContainingFiles(FileTypeIndex.NAME, LombokConfigFileType.INSTANCE,
        GlobalSearchScope.allScope(project));
    for (VirtualFile virtualFile : virtualFiles) {
      LombokConfigFile lombokConfigFile = (LombokConfigFile) PsiManager.getInstance(project).findFile(virtualFile);
      LombokConfigProperty[] properties = getLombokConfigProperties(lombokConfigFile);
      Collections.addAll(result, properties);
    }
    return result;
  }

  @NotNull
  public static LombokConfigProperty[] getLombokConfigProperties(@Nullable LombokConfigFile lombokConfigFile) {
    LombokConfigProperty[] result = null;
    if (null != lombokConfigFile) {
      result = PsiTreeUtil.getChildrenOfType(lombokConfigFile, LombokConfigProperty.class);
    }
    return null == result ? EMPTY_LOMBOK_CONFIG_PROPERTIES : result;
  }
}