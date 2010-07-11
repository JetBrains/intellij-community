/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.validation;

import org.intellij.plugins.relaxNG.compact.RncFileType;
import org.intellij.plugins.relaxNG.model.resolve.RelaxIncludeIndex;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.util.XmlUtil;

import com.thaiopensource.relaxng.impl.SchemaReaderImpl;
import com.thaiopensource.util.PropertyMap;
import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.validate.Schema;
import com.thaiopensource.xml.sax.Sax2XMLReaderCreator;
import com.thaiopensource.xml.sax.XMLReaderCreator;
import org.kohsuke.rngom.ast.builder.BuildException;
import org.kohsuke.rngom.ast.builder.IncludedGrammar;
import org.kohsuke.rngom.ast.builder.SchemaBuilder;
import org.kohsuke.rngom.ast.om.ParsedPattern;
import org.kohsuke.rngom.ast.util.CheckingSchemaBuilder;
import org.kohsuke.rngom.digested.DPattern;
import org.kohsuke.rngom.digested.DSchemaBuilderImpl;
import org.kohsuke.rngom.parse.IllegalSchemaException;
import org.kohsuke.rngom.parse.Parseable;
import org.kohsuke.rngom.parse.compact.CompactParseable;
import org.kohsuke.rngom.parse.xml.SAXParseable;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.StringReader;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 19.07.2007
*/
public class RngParser {
  static final Key<CachedValue<Schema>> SCHEMA_KEY = Key.create("SCHEMA");
  static final Key<CachedValue<DPattern>> PATTERN_KEY = Key.create("PATTERN");

  public static final DefaultHandler DEFAULT_HANDLER = new DefaultHandler() {
    public void error(SAXParseException e) throws SAXException {
      System.out.println("e.getMessage() = " + e.getMessage() + " [" + e.getSystemId() + "]");
    }
  };

  static final PropertyMap EMPTY_PROPS = new PropertyMapBuilder().toPropertyMap();

  public static DPattern getCachedPattern(final PsiFile descriptorFile, final ErrorHandler eh) {
    final CachedValuesManager mgr = descriptorFile.getManager().getCachedValuesManager();

    return mgr.getCachedValue(descriptorFile, PATTERN_KEY, new CachedValueProvider<DPattern>() {
      public Result<DPattern> compute() {
        return Result.create(parsePattern(descriptorFile, eh, false), descriptorFile);
      }
    }, false);
  }

  public static DPattern parsePattern(final PsiFile file, final ErrorHandler eh, boolean checking) {
    try {
      final Parseable p = createParsable(file, eh);
      final SchemaBuilder sb = new DSchemaBuilderImpl();

      return (DPattern)p.parse(checking ? new CheckingSchemaBuilder(sb, eh) : sb);
    } catch (BuildException e) {
      e.printStackTrace();
    } catch (IllegalSchemaException e) {
      System.out.println("invalid schema: " + file.getVirtualFile().getPresentableUrl());
    }
    return null;
  }

  private static Parseable createParsable(final PsiFile file, final ErrorHandler eh) {
    final InputSource source = makeInputSource(file);

    if (file.getFileType() == RncFileType.getInstance()) {
      return new CompactParseable(source, eh) {
        public ParsedPattern parseInclude(String uri, SchemaBuilder schemaBuilder, IncludedGrammar g, String inheritedNs)
                throws BuildException, IllegalSchemaException
        {
          return super.parseInclude(resolveURI(file, uri), schemaBuilder, g, inheritedNs);
        }
      };
    } else {
      return new SAXParseable(source, eh) {
        public ParsedPattern parseInclude(String uri, SchemaBuilder schemaBuilder, IncludedGrammar g, String inheritedNs)
                throws BuildException, IllegalSchemaException
        {
          return super.parseInclude(resolveURI(file, uri), schemaBuilder, g, inheritedNs);
        }
      };
    }
  }

  public static String resolveURI(PsiFile descriptorFile, String s) {
    final PsiFile file = XmlUtil.findXmlFile(descriptorFile, s);

    if (file != null) {
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        final PsiDocumentManager dm = PsiDocumentManager.getInstance(file.getProject());
        final Document d = dm.getCachedDocument(file);
        if (d != null) {
          // TODO: fix. write action + saving -> deadlock
//          dm.commitDocument(d);
//          FileDocumentManager.getInstance().saveDocument(d);
        }
        s = reallyFixIDEAUrl(virtualFile.getUrl());
      }
    }
    return s;
  }

  public static String reallyFixIDEAUrl(String url) {
    String s = VfsUtil.fixIDEAUrl(url);
    if (!SystemInfo.isWindows) {
      // Linux:
      //    "file://tmp/foo.bar"  (produced by com.intellij.openapi.vfs.VfsUtil.fixIDEAUrl) doesn't work: "java.net.UnknownHostException: tmp"
      //    "file:/tmp/foo.bar"   (produced by File.toURL()) works fine
      s = s.replaceFirst("file:/+", "file:/");
    }
    return s;
  }

  public static Schema getCachedSchema(final XmlFile descriptorFile) {
    CachedValue<Schema> value = descriptorFile.getUserData(SCHEMA_KEY);
    if (value == null) {
      final CachedValueProvider<Schema> provider = new CachedValueProvider<Schema>() {
        public Result<Schema> compute() {
          final InputSource inputSource = makeInputSource(descriptorFile);

          try {
            final Schema schema = new MySchemaReader(descriptorFile).createSchema(inputSource, EMPTY_PROPS);
            final PsiElementProcessor.CollectElements<XmlFile> processor = new PsiElementProcessor.CollectElements<XmlFile>();
            RelaxIncludeIndex.processForwardDependencies(descriptorFile, processor);
            if (processor.getCollection().size() > 0) {
              return Result.create(schema, processor.toArray(), descriptorFile);
            } else {
              return Result.createSingleDependency(schema, descriptorFile);
            }
          } catch (Exception e) {
            e.printStackTrace();
            return Result.createSingleDependency(null, descriptorFile);
          }
        }
      };

      final CachedValuesManager mgr = descriptorFile.getManager().getCachedValuesManager();
      value = mgr.createCachedValue(provider,  false);
      descriptorFile.putUserData(SCHEMA_KEY, value);
    }
    return value.getValue();
  }

  private static InputSource makeInputSource(PsiFile descriptorFile) {
    final InputSource inputSource = new InputSource(new StringReader(descriptorFile.getText()));
    final VirtualFile file = descriptorFile.getVirtualFile();
    if (file != null) {
      inputSource.setSystemId(reallyFixIDEAUrl(file.getUrl()));
    }
    return inputSource;
  }

  static class MySchemaReader extends SchemaReaderImpl {
    private final PsiFile myDescriptorFile;

    public MySchemaReader(PsiFile descriptorFile) {
      myDescriptorFile = descriptorFile;
    }

    protected com.thaiopensource.relaxng.parse.Parseable createParseable(XMLReaderCreator xmlReaderCreator, InputSource inputSource, ErrorHandler errorHandler) {
      if (myDescriptorFile.getFileType() == RncFileType.getInstance()) {
        return new com.thaiopensource.relaxng.parse.compact.CompactParseable(inputSource, errorHandler);
      } else {
        return new com.thaiopensource.relaxng.parse.sax.SAXParseable(new Sax2XMLReaderCreator(), inputSource, errorHandler);
      }
    }
  }
}