// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.index;

import com.intellij.ide.highlighter.DTDFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public final class XmlNamespaceIndex extends XmlIndex<XsdNamespaceBuilder> {
  private static final String LOCAL_SCHEMA_ID = "$LOCAL_SCHEMA$";

  @Nullable
  public static String getNamespace(@NotNull VirtualFile file, @NotNull Project project) {
    if (DumbService.isDumb(project) || XmlUtil.isStubBuilding()) {
      return computeNamespace(file);
    }
    XsdNamespaceBuilder item = getFileNamespace(file, project);
    if (item == null) {
      return null;
    }
    String namespace = item.getNamespace();
    return namespace != null ? namespace : file.getUrl();
  }

  @Nullable
  public static String computeNamespace(@NotNull VirtualFile file) {
    try (InputStream stream = file.getInputStream()) {
      return XsdNamespaceBuilder.computeNamespace(stream);
    }
    catch (IOException e) {
      return null;
    }
  }

  @NotNull
  public static List<IndexedRelevantResource<String, XsdNamespaceBuilder>> getResourcesByNamespace(@NotNull String namespace,
                                                                                                   @NotNull Project project,
                                                                                                   @Nullable Module module) {
    List<IndexedRelevantResource<String, XsdNamespaceBuilder>> resources = IndexedRelevantResource.getResources(NAME, namespace, module, project, null);
    resources.addAll(getDtdResources(namespace, module, project));
    ContainerUtil.addIfNotNull(resources, getResourceByLocalFile(namespace, project, module));
    Collections.sort(resources);
    return resources;
  }

  @Nullable
  private static IndexedRelevantResource<String, XsdNamespaceBuilder> getResourceByLocalFile(@NotNull String namespace,
                                                                                             @NotNull Project project,
                                                                                             @Nullable Module module) {
    String protocol = VirtualFileManager.extractProtocol(namespace);
    VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    if (virtualFileManager.getFileSystem(protocol) instanceof LocalFileSystem) {
      VirtualFile file = virtualFileManager.findFileByUrl(namespace);
      if (file != null) {
        XsdNamespaceBuilder xsdNamespaceBuilder = getFileNamespace(file, project);
        if (xsdNamespaceBuilder != null) {
          ResourceRelevance relevance = ResourceRelevance.getRelevance(file, module, ProjectFileIndex.getInstance(project), null);
          return new IndexedRelevantResource<>(file, file.getUrl(), xsdNamespaceBuilder, relevance);
        }
      }
    }
    return null;
  }

  @Nullable
  private static XsdNamespaceBuilder getFileNamespace(@NotNull VirtualFile file, @NotNull Project project) {
    if (FileTypeRegistry.getInstance().isFileOfType(file, DTDFileType.INSTANCE)) {
      return new XsdNamespaceBuilder(file.getName(), "", Collections.emptyList(), Collections.emptyList());
    }
    Map<String, XsdNamespaceBuilder> data = FileBasedIndex.getInstance().getFileData(NAME, file, project);
    return ContainerUtil.getFirstItem(data.values());
  }

  public static List<IndexedRelevantResource<String, XsdNamespaceBuilder>> getAllResources(@Nullable final Module module,
                                                                                           @NotNull Project project) {
    List<IndexedRelevantResource<String, XsdNamespaceBuilder>> xmlResources = IndexedRelevantResource.getAllResources(NAME, module, project, null);
    List<IndexedRelevantResource<String, XsdNamespaceBuilder>> dtdResources = getDtdResources(null, module, project);
    return ContainerUtil.concat(xmlResources, dtdResources);
  }

  @NotNull
  private static List<IndexedRelevantResource<String, XsdNamespaceBuilder>> getDtdResources(@Nullable String namespace,
                                                                                            @Nullable Module module,
                                                                                            @NotNull Project project) {
    AdditionalIndexedRootsScope scope = new AdditionalIndexedRootsScope(GlobalSearchScope.allScope(project));
    ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    Function<VirtualFile, IndexedRelevantResource<String, XsdNamespaceBuilder>> resourceFunction = f -> {
      ResourceRelevance relevance = ResourceRelevance.getRelevance(f, module, index, scope);
      return new IndexedRelevantResource<>(f, f.getName(), getFileNamespace(f, project), relevance);
    };
    Collection<VirtualFile> dtdFiles;
    if (namespace == null) {
      dtdFiles = FileTypeIndex.getFiles(DTDFileType.INSTANCE, scope);
    } else {
      dtdFiles = ContainerUtil.filter(FilenameIndex.getVirtualFilesByName(project, namespace, scope), f -> FileTypeRegistry.getInstance().isFileOfType(f, DTDFileType.INSTANCE));
    }
    return ContainerUtil.map(dtdFiles, resourceFunction);
  }

  public static final ID<String,XsdNamespaceBuilder> NAME = ID.create("XmlNamespaces");

  @Override
  @NotNull
  public ID<String, XsdNamespaceBuilder> getName() {
    return NAME;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
      @Override
      public boolean acceptInput(@NotNull final VirtualFile file) {
        return "xsd".equals(file.getExtension());
      }
    };
  }

  @Override
  @NotNull
  public DataIndexer<String, XsdNamespaceBuilder, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      @NotNull
      public Map<String, XsdNamespaceBuilder> map(@NotNull final FileContent inputData) {
        XsdNamespaceBuilder builder =
          XsdNamespaceBuilder.computeNamespace(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()));
        String namespace = builder.getNamespace();
        return Collections.singletonMap(ObjectUtils.notNull(namespace, LOCAL_SCHEMA_ID), builder);
      }
    };
  }

  private static final String NULL_STRING = "\"\"";

  @NotNull
  @Override
  public DataExternalizer<XsdNamespaceBuilder> getValueExternalizer() {
    return new DataExternalizer<>() {
      @Override
      public void save(@NotNull DataOutput out, XsdNamespaceBuilder value) throws IOException {
        IOUtil.writeUTF(out, value.getNamespace() != null ? value.getNamespace() : NULL_STRING);
        IOUtil.writeUTF(out, value.getVersion() != null ? value.getVersion() : NULL_STRING);
        IOUtil.writeStringList(out, value.getTags());
        IOUtil.writeStringList(out, value.getRootTags());
      }

      @Override
      public XsdNamespaceBuilder read(@NotNull DataInput in) throws IOException {
        String namespace = IOUtil.readUTF(in);
        if (NULL_STRING.equals(namespace)) namespace = null;
        String version = IOUtil.readUTF(in);
        if (NULL_STRING.equals(version)) version = null;

        return new XsdNamespaceBuilder(namespace,
                                       version,
                                       IOUtil.readStringList(in),
                                       IOUtil.readStringList(in));
      }
    };
  }

  @Override
  public int getVersion() {
    return 8;
  }

  @Nullable
  public static IndexedRelevantResource<String, XsdNamespaceBuilder> guessSchema(String namespace,
                                                                                 @Nullable final String tagName,
                                                                                 @Nullable final String version,
                                                                                 @Nullable String schemaLocation,
                                                                                 @Nullable Module module,
                                                                                 @NotNull Project project) {

    final List<IndexedRelevantResource<String, XsdNamespaceBuilder>>
      resources = getResourcesByNamespace(namespace, project, module);

    if (resources.isEmpty()) return null;
    if (resources.size() == 1) return resources.get(0);
    final String fileName = schemaLocation == null ? null : new File(schemaLocation).getName();
    IndexedRelevantResource<String, XsdNamespaceBuilder> resource =
      Collections.max(resources, (o1, o2) -> {
        if (fileName != null) {
          int i = Comparing.compare(fileName.equals(o1.getFile().getName()), fileName.equals(o2.getFile().getName()));
          if (i != 0) return i;
        }
        if (tagName != null) {
          int i = Comparing.compare(o1.getValue().hasTag(tagName), o2.getValue().hasTag(tagName));
          if (i != 0) return i;
        }
        int i = o1.compareTo(o2);
        if (i != 0) return i;
        return o1.getValue().getRating(tagName, version) - o2.getValue().getRating(tagName, version);
      });
    if (tagName != null && !resource.getValue().hasTag(tagName)) {
      return null;
    }
    return resource;
  }

  @Nullable
  public static XmlFile guessSchema(String namespace,
                                    @Nullable final String tagName,
                                    @Nullable final String version,
                                    @Nullable String schemaLocation,
                                    @NotNull PsiFile file) {

    if (DumbService.isDumb(file.getProject()) || XmlUtil.isStubBuilding()) return null;

    IndexedRelevantResource<String,XsdNamespaceBuilder> resource =
      guessSchema(namespace, tagName, version, schemaLocation, ModuleUtilCore.findModuleForPsiElement(file), file.getProject());
    if (resource == null) return null;
    return findSchemaFile(resource.getFile(), file);
  }

  @Nullable
  private static XmlFile findSchemaFile(VirtualFile resourceFile, PsiFile baseFile) {
    PsiFile psiFile = baseFile.getManager().findFile(resourceFile);
    return psiFile instanceof XmlFile ? (XmlFile)psiFile : null;
  }

  @Nullable
  public static XmlFile guessDtd(String dtdUri, @NotNull PsiFile baseFile) {

    if (!dtdUri.endsWith(".dtd") ||
        DumbService.isDumb(baseFile.getProject()) ||
        XmlUtil.isStubBuilding()) return null;

    String dtdFileName = new File(dtdUri).getName();
    List<IndexedRelevantResource<String, XsdNamespaceBuilder>>
      list = getResourcesByNamespace(dtdFileName, baseFile.getProject(), ModuleUtilCore.findModuleForPsiElement(baseFile));
    if (list.isEmpty()) {
      return null;
    }
    IndexedRelevantResource<String, XsdNamespaceBuilder> resource;
    if (list.size() > 1) {
      final String[] split = dtdUri.split("/");
      resource = Collections.max(list, new Comparator<IndexedRelevantResource<String, XsdNamespaceBuilder>>() {
        @Override
        public int compare(IndexedRelevantResource<String, XsdNamespaceBuilder> o1,
                           IndexedRelevantResource<String, XsdNamespaceBuilder> o2) {

          return weight(o1) - weight(o2);
        }

        int weight(IndexedRelevantResource<String, XsdNamespaceBuilder> o1) {
          VirtualFile file = o1.getFile();
          for (int i = split.length - 1; i >= 0 && file != null; i--) {
            String s = split[i];
            if (!s.equals(file.getName())) {
              return split.length - i;
            }
            file = file.getParent();
          }
          return 0;
        }
      });
    }
    else {
      resource = list.get(0);
    }
    return findSchemaFile(resource.getFile(), baseFile);
  }
}
