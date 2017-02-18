/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.index;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
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
public class XmlNamespaceIndex extends XmlIndex<XsdNamespaceBuilder> {

  @Nullable
  public static String getNamespace(@NotNull VirtualFile file, final Project project, PsiFile context) {
    if (DumbService.isDumb(project) || (context != null && XmlUtil.isStubBuilding())) {
      return computeNamespace(file);
    }
    final List<XsdNamespaceBuilder> list = FileBasedIndex.getInstance().getValues(NAME, file.getUrl(), createFilter(project));
    return list.size() == 0 ? null : list.get(0).getNamespace();
  }

  @Nullable
  public static String computeNamespace(@NotNull VirtualFile file) {
    InputStream stream = null;
    try {
      stream = file.getInputStream();
      return XsdNamespaceBuilder.computeNamespace(stream);
    }
    catch (IOException e) {
      return null;
    }
    finally {
      StreamUtil.closeStream(stream);
    }
  }

  public static List<IndexedRelevantResource<String, XsdNamespaceBuilder>> getResourcesByNamespace(String namespace,
                                                                                                   @NotNull Project project,
                                                                                                   @Nullable Module module) {
    List<IndexedRelevantResource<String, XsdNamespaceBuilder>> resources =
      IndexedRelevantResource.getResources(NAME, namespace, module, project, null);
    Collections.sort(resources);
    return resources;
  }

  public static List<IndexedRelevantResource<String, XsdNamespaceBuilder>> getAllResources(@Nullable final Module module,
                                                                                           @NotNull Project project,
                                                                                           @Nullable NullableFunction<List<IndexedRelevantResource<String, XsdNamespaceBuilder>>, IndexedRelevantResource<String, XsdNamespaceBuilder>> chooser) {
    return IndexedRelevantResource.getAllResources(NAME, module, project, chooser);
  }

  public static final ID<String,XsdNamespaceBuilder> NAME = ID.create("XmlNamespaces");

  @Override
  @NotNull
  public ID<String, XsdNamespaceBuilder> getName() {
    return NAME;
  }

  @Override
  @NotNull
  public DataIndexer<String, XsdNamespaceBuilder, FileContent> getIndexer() {
    return new DataIndexer<String, XsdNamespaceBuilder, FileContent>() {
      @Override
      @NotNull
      public Map<String, XsdNamespaceBuilder> map(@NotNull final FileContent inputData) {
        final XsdNamespaceBuilder builder;
        if ("dtd".equals(inputData.getFile().getExtension())) {
          builder = new XsdNamespaceBuilder(inputData.getFileName(), "", Collections.<String>emptyList(), Collections.<String>emptyList());
        }
        else {
          builder = XsdNamespaceBuilder.computeNamespace(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()));
        }
        final HashMap<String, XsdNamespaceBuilder> map = new HashMap<>(2);
        String namespace = builder.getNamespace();
        if (namespace != null) {
          map.put(namespace, builder);
        }
        // so that we could get ns by file url (see getNamespace method above)
        map.put(inputData.getFile().getUrl(), builder);
        return map;
      }
    };
  }

  private static final String NULL_STRING = "\"\"";

  @NotNull
  @Override
  public DataExternalizer<XsdNamespaceBuilder> getValueExternalizer() {
    return new DataExternalizer<XsdNamespaceBuilder>() {
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
    return 5;
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
