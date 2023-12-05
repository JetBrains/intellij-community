package de.plushnikov.intellij.plugin.util;

import com.intellij.java.library.JavaLibraryUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.concurrency.ThreadingAssertions;
import de.plushnikov.intellij.plugin.LombokClassNames;
import de.plushnikov.intellij.plugin.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class LombokLibraryUtil {

  private static final String LOMBOK_PACKAGE = "lombok.experimental";

  public static boolean hasLombokLibrary(@NotNull Project project) {
    if (project.isDefault() || !project.isInitialized()) {
      return false;
    }

    ThreadingAssertions.assertReadAccess();
    return JavaLibraryUtil.hasLibraryJar(project, "org.projectlombok:lombok")
           || detectLombokJarsSlow(project);
  }

  public static boolean hasLombokClasses(@Nullable Module module) {
    return JavaLibraryUtil.hasLibraryClass(module, LombokClassNames.GETTER);
  }

  private static boolean detectLombokJarsSlow(Project project) {
    // it is required for JARs attached directly from disk
    // or via build systems that do not supply Maven coordinates properly via LibraryWithMavenCoordinatesProperties
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      OrderEnumerator orderEnumerator = OrderEnumerator.orderEntries(project);

      Ref<Boolean> exists = new Ref<>(false);
      orderEnumerator.recursively()
        .forEachLibrary(library -> {
          VirtualFile[] libraryFiles = library.getFiles(OrderRootType.CLASSES);
          JarFileSystem jarFileSystem = JarFileSystem.getInstance();

          for (VirtualFile libraryFile : libraryFiles) {
            if (libraryFile.getFileSystem() != jarFileSystem) continue;

            // look into every JAR for top level package entry
            if (libraryFile.findChild("lombok") != null) {
              exists.set(true);
              return false;
            }
          }

          return true;
        });

      return Result.create(exists.get(), ProjectRootManager.getInstance(project));
    });
  }

  public static @NotNull String getLombokVersionCached(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      String lombokVersion = null;
      try {
        lombokVersion = ReadAction.nonBlocking(() -> getLombokVersionInternal(project)).executeSynchronously();
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        Logger.getInstance(LombokLibraryUtil.class).error(e);
      }
      return new Result<>(StringUtil.notNullize(lombokVersion), ProjectRootManager.getInstance(project));
    });
  }

  private static @Nullable String getLombokVersionInternal(@NotNull Project project) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(LOMBOK_PACKAGE);
    if (aPackage != null) {
      PsiDirectory[] directories = aPackage.getDirectories();
      if (directories.length > 0) {
        List<OrderEntry> entries =
          ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(directories[0].getVirtualFile());
        if (!entries.isEmpty()) {
          return Version.parseLombokVersion(entries.get(0));
        }
      }
    }
    return null;
  }
}
