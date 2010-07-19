/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * XSD/DTD Model generator tool
 *
 * By Gregory Shrago
 * 2002 - 2006
 */
package com.intellij.util.dom.generator;

import java.util.*;
import java.io.File;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author Gregory.Shrago
 * @author Konstantin Bulenkov
 */
public class JetBrainsEmitter implements Emitter {
  static final boolean NOT_COMPARE_MODE = true; // compare mode: skip package decl & all comments/javadoc
  static final boolean JB_OFF = false;
  static final boolean REPLACE_TYPES_WITH_INTERFACES = true;
  private String AUTHOR = null;


  public void emit(FileManager fileManager, ModelDesc model, File outputRoot) {
    final NamespaceDesc nsdDef = model.nsdMap.get("");
    final Set<String> simpleTypes = new TreeSet<String>();
    for (TypeDesc td : model.jtMap.values()) {
      generateClass(fileManager, td, model, outputRoot, simpleTypes);
    }
//        for (Iterator it = nsdMap.values().iterator(); it.hasNext(); ) {
//            NamespaceDesc nsd = (NamespaceDesc) it.next();
//            generateSuper(nsd, outputRoot);
//            generateHelper(nsd, jtMap,  outputRoot);
//        }
    generateSuper(fileManager, nsdDef, model, outputRoot);
    generateHelper(fileManager, nsdDef, model, outputRoot);

    Util.log("SimpleTypes log:");
    for (String s : simpleTypes) {
      Util.log("  " + s);
    }
  }

  public void generateClass(FileManager fileManager, TypeDesc td, ModelDesc model, File outDir, Set<String> simpleTypes) {
    final Map<String, TypeDesc> jtMap = model.jtMap;
    final Map<String, NamespaceDesc> nsdMap = model.nsdMap;
    final NamespaceDesc nsd = nsdMap.get(nsdMap.containsKey(td.xsNamespace) ? td.xsNamespace : "");
    final String typeName = td.name;
    final String typeQName = model.getNSDPrefix(td) + typeName;
    final String pkgName = typeQName.lastIndexOf('.') > -1 ? typeQName.substring(0, typeQName.lastIndexOf('.')) : "";

    final File outFile = fileManager.getOutputFile(new File(outDir, toJavaFileName(typeQName)));
    PrintWriter out = null;
    try {
      TreeSet<String> externalClasses = new TreeSet<String>();
      if (td.type != TypeDesc.TypeEnum.ENUM) {
        if (nsd.imports != null) {
          StringTokenizer st = new StringTokenizer(nsd.imports, ";");
          while (st.hasMoreTokens()) {
            externalClasses.add(st.nextToken());
          }
        }
        if (!model.getNSDPrefix("", nsd.superClass, false).equals(model.getNSDPrefix(td))) {
          externalClasses.add(model.getNSDPrefix("", nsd.superClass, false) + nsd.superClass.substring(nsd.superClass.lastIndexOf(".") + 1));
        }
        if (td.supers != null) {
          for (TypeDesc tds : td.supers) {
            String pkg1 = model.getNSDPrefix(tds);
            String pkg2 = model.getNSDPrefix(td);
            if (!pkg1.equals(pkg2)) {
              externalClasses.add(model.getNSDPrefix(tds) + tds.name);
            }
          }
        }
        for (FieldDesc fd : td.fdMap.values()) {
          if (fd.simpleTypesString != null && fd.simpleTypesString.indexOf(":fully-qualified-classType;") != -1) {
            externalClasses.add("com.intellij.psi.PsiClass");
          }
          if (fd.contentQualifiedName != null && fd.contentQualifiedName.indexOf('.') > 0) {
            String pkgNameFD = fd.contentQualifiedName.substring(0, fd.contentQualifiedName.lastIndexOf('.'));
            if (!pkgNameFD.equals(pkgName)) {
              externalClasses.add(fd.contentQualifiedName);
            }
          }
          if (fd.clType < 0) {
            externalClasses.add("java.util.List");
          }
          externalClasses.add("org.jetbrains.annotations.NotNull");
          if (fd.required) {
            externalClasses.add("com.intellij.util.xml.Required");
          }
          if (fd.duplicateIndex >= 0 || fd.clType == FieldDesc.BOOL) {
            externalClasses.add("com.intellij.util.xml.SubTag");
          }
          if (fd.clType == FieldDesc.ATTR) {
            externalClasses.add("com.intellij.util.xml.GenericAttributeValue");
          } else if (Math.abs(fd.clType) != FieldDesc.SIMPLE) {
            if (Math.abs(fd.clType) == FieldDesc.OBJ) {
              final TypeDesc ftd = jtMap.get(fd.contentQualifiedName);
              if (ftd != null && ftd.type == TypeDesc.TypeEnum.ENUM) {
                externalClasses.add("com.intellij.util.xml.GenericDomValue");
              }
            } else {
              externalClasses.add("com.intellij.util.xml.GenericDomValue");
            }
          }
        }
      }

      Util.log("Generating type: " + typeName + "..");
      out = new PrintWriter(new FileWriter(outFile));
      if (NOT_COMPARE_MODE) {
        out.println("// Generated on " + new Date());
        out.println("// DTD/Schema  :    " + nsd.name);
      }
      out.println("");
      if (NOT_COMPARE_MODE && pkgName != null && pkgName.length() > 0) {
        out.println("package " + pkgName + ";");
      }
      out.println();
      if (td.type != TypeDesc.TypeEnum.ENUM) {
        boolean javaLang = false;
        boolean external = false;
        for (String s : externalClasses) {
          if (s.startsWith("java.")) {
            javaLang = true;
            continue;
          }
          external = true;
          out.println("import " + s + ";");
        }
        if (javaLang) {
          if (external) out.println();
          for (String s : externalClasses) {
            if (!s.startsWith("java.")) continue;
            out.println("import " + s + ";");
          }
        }
        out.println();
      }
      if (td.type == TypeDesc.TypeEnum.ENUM) {
        boolean text = false;
        for (Map.Entry<String, FieldDesc> e : td.fdMap.entrySet()) {
          if (!e.getKey().equals(e.getValue().name)) {
            text = true;
            break;
          }
        }
        if (NOT_COMPARE_MODE) {
          out.println(JDOC_OPEN);
          out.println(JDOC_CONT + td.xsNamespace + ":" + td.xsName + " enumeration.");
          if (AUTHOR != null) out.println(JDOC_CONT + AUTHOR);
          printDocumentation(out, td.documentation, JDOC_CONT);

          out.println(JDOC_CLOSE);
        }
        out.print("public enum " + typeName + (text ? (JB_OFF ? "" : " implements com.intellij.util.xml.NamedEnum") : ""));
        out.print(" {");
        boolean first = true;
        for (Map.Entry<String, FieldDesc> e : td.fdMap.entrySet()) {
          String val = e.getKey();
          FieldDesc id = e.getValue();
          if (first) {
            first = false;
            out.println("");
          } else {
            out.println(",");
          }
          if (text) {
            out.print("\t" + id.name + " (\"" + val + "\")");
          } else {
            out.print("\t" + id.name);
          }
        }
        if (text) {
          out.println(";");
          out.println();
          out.println("\tprivate final String value;");
          out.println("\tprivate " + typeName + "(String value) { this.value = value; }");
          out.println("\tpublic String getValue() { return value; }");
        }
        out.println();
        out.println("}");
        return;
      }
      if (NOT_COMPARE_MODE) {
        out.println(JDOC_OPEN);
        if (td.type == TypeDesc.TypeEnum.GROUP_INTERFACE) {
          out.println(JDOC_CONT + td.xsNamespace + ":" + td.xsName + " model group interface.");
        } else {
          out.println(JDOC_CONT + td.xsNamespace + ":" + td.xsName + " interface.");
        }
        printDocumentation(out, td.documentation, JDOC_CONT);
        if (AUTHOR != null) out.println(JDOC_CONT + AUTHOR);
        out.println(JDOC_CLOSE);
      }
      out.print("public interface " + typeName);
      if (nsd.superClass != null || (td.supers != null && td.supers.length > 1)) {
        boolean comma = false;
        if (td.type != TypeDesc.TypeEnum.GROUP_INTERFACE) {
          if (nsd.superClass != null) {
            out.print(" extends " + nsd.superClass.substring(nsd.superClass.lastIndexOf(".") + 1));
            comma = true;
          }
        }
        if (td.supers != null && td.supers.length > 0) {
          if (!comma) out.print(" extends ");
          for (TypeDesc aSuper : td.supers) {
            if (comma) out.print(", ");
            else comma = true;
            out.print(aSuper.name);
          }
        }
      }
      out.println(" {");

      FieldDesc[] fields = td.fdMap.values().toArray(new FieldDesc[td.fdMap.size()]);
      if (fields.length == 0) {
        Util.logwarn("no fields in: " + td.xsName);
      }
      Arrays.sort(fields, new Comparator<FieldDesc>() {
        public int compare(FieldDesc o1, FieldDesc o2) {
          return o1.realIndex - o2.realIndex;
        }
      });
      /*
// fields
out.println("");
out.println("\t// fields");
for (int i = 0; i < fields.length; i++) {
  out.print("\t// ");
  out.print(fields[i].tagName);
  out.print(", required:" + fields[i].required);
  out.print(", cltype:" + fields[i].clType);
  out.println("");
  String[] doc = parseAnnotationString(fields[i].documentation);
  for (int dc=0; dc<doc.length; dc++) {
    out.println("\t// "+doc[dc]);
  }

  if (fields[i].comment != null) {
    out.print("\t// ");
    out.print(fields[i].comment);
    out.println("");
  }
  out.print("\tprivate ");
  out.print(fields[i].type);
  out.print(" ");
  out.print(fields[i].name);
  out.print(" = ");
  out.print(fields[i].def);
  out.print(";");
  out.println("");
  out.println("");
      }
      */

      // constructors

      // set/get methods
      // static gets
      /*
      out.println("\t// standard getters");
out.println("\t" + JDOC_OPEN);
out.println("\t" + JDOC_CONT + "Returns \"" + defTagName + "\"");
out.println("\t" + JDOC_CLOSE);
out.println("\tpublic String get_tag_name() { return _tag_name; }");
out.println("\t" + JDOC_OPEN);
out.println("\t" + JDOC_CONT + "Returns child node tag names");
out.println("\t" + JDOC_CLOSE);
out.println("\tpublic String[] get_tag_names() { return (String[])_tag_names.clone(); }");
out.println("\t" + JDOC_OPEN);
out.println("\t" + JDOC_CONT + "Returns child node field names");
out.println("\t" + JDOC_CLOSE);
out.println("\tpublic String[] get_field_names() { return (String[])_field_names.clone(); }");
out.println("");
out.println("\t// getters/setters methods");
      */

      out.println("");
      for (FieldDesc field : fields) {
        String tagName = field.tagName;
        String type = field.type;
        String elementType = field.elementType;
        String name = field.name;
        String paramName = toJavaIdName(field.clType > 0 ? name : field.elementName);
        String javaDocTagName = field.clType < 0 ? tagName + " children" : tagName != null ? tagName + " child" : "simple content";
        boolean isAttr = field.clType == FieldDesc.ATTR;
        boolean isList = field.clType < 0;

        if (name.equals("class")) { // getClass method prohibited
          name = "clazz";
        }
        boolean nameChanged = field.tagName != null && !name.equals(isList ? Util.pluralize(Util.toJavaFieldName(field.tagName)) : Util.toJavaFieldName(field.tagName));

        // annotations
        // types replacement
        String newType = field.clType < 0 ? elementType : type;
        String converterString = null;
        if (field.simpleTypesString != null) {
          if (field.simpleTypesString.indexOf(":fully-qualified-classType;") != -1) { // localType, remoteType, etc.
            newType = "PsiClass";
            //converterString = (JB_OFF ? "//" : "")+"\t@Convert (PsiClassReferenceConverter.class)";
          } else if (field.simpleTypesString.indexOf(":ejb-linkType;") != -1) {
          } else if (field.simpleTypesString.indexOf(":ejb-ref-nameType;") != -1) { // jndi-nameType
          } else if (field.simpleTypesString.indexOf(":pathType;") != -1) {
          } else if (field.simpleTypesString.indexOf(":java-identifierType;") != -1) {
            //out.println((JB_OFF ? "//" : "") +"\t@Convert (JavaIdentifierConverter.class)");
          } else if (field.simpleTypesString.indexOf(":QName;") != -1) {
            // ???
          } else if (field.simpleTypesString.indexOf(":integer;") != -1) { // BigDecimal
            newType = REPLACE_TYPES_WITH_INTERFACES ? "Integer" : "int";
          } else if (field.simpleTypesString.indexOf(":int;") != -1) {
            newType = REPLACE_TYPES_WITH_INTERFACES ? "Integer" : "int";
          } else if (field.simpleTypesString.indexOf(":byte;") != -1) {
            newType = REPLACE_TYPES_WITH_INTERFACES ? "Byte" : "byte";
          } else if (field.simpleTypesString.indexOf(":short;") != -1) {
            newType = REPLACE_TYPES_WITH_INTERFACES ? "Short" : "short";
          } else if (field.simpleTypesString.indexOf(":long;") != -1) {
            newType = REPLACE_TYPES_WITH_INTERFACES ? "Long" : "long";
          } else if (field.simpleTypesString.indexOf(":float;") != -1) {
            newType = REPLACE_TYPES_WITH_INTERFACES ? "Float" : "float";
          } else if (field.simpleTypesString.indexOf(":double;") != -1) {
            newType = REPLACE_TYPES_WITH_INTERFACES ? "Double" : "double";
          } else if (field.simpleTypesString.indexOf(":boolean;") != -1) { // true-falseType
            newType = REPLACE_TYPES_WITH_INTERFACES ? "Boolean" : "boolean";
          }
          for (int idx = 0; idx != -1;) {
            simpleTypes.add(field.simpleTypesString.substring(idx));
            idx = field.simpleTypesString.indexOf(';', idx) + 1;
            if (idx == 0) break;
          }
        }
        if (REPLACE_TYPES_WITH_INTERFACES) {
          switch (Math.abs(field.clType)) {
            case FieldDesc.ATTR:
              newType = "GenericAttributeValue<" + newType + ">";
              break;
            case FieldDesc.BOOL:
              newType = "GenericDomValue<Boolean>";
              break;
            case FieldDesc.SIMPLE:
              break;
            case FieldDesc.STR:
              newType = "GenericDomValue<" + newType + ">";
              break;
            case FieldDesc.OBJ: {
              TypeDesc ftd = jtMap.get(field.contentQualifiedName);
              if (ftd != null && ftd.type == TypeDesc.TypeEnum.ENUM) {
                newType = "GenericDomValue<" + ftd.name + ">";
              }
              break;
            }
          }
        }
        if (newType != null && isList) {
          elementType = newType;
        } else if (newType != null) {
          type = newType;
        }
        if (isList) {
          type = "List<" + elementType + ">";
        }

        StringBuffer sbAnnotations = new StringBuffer();
        if (field.clType == FieldDesc.SIMPLE) {
          //  sbAnnotations.append((JB_OFF ? "//" : "") +"\t@TagValue");
        } else if (isAttr && nameChanged) {
          sbAnnotations.append((JB_OFF ? "//" : "") + "\t@com.intellij.util.xml.Attribute (\"").append(tagName).append("\")");
        } else if (isList) {
          // sbAnnotations.append((JB_OFF ? "//" : "") +"\t@SubTagList (\"" + tagName + "\")");
          if (nameChanged) {
            sbAnnotations.append((JB_OFF ? "//" : "") + "\t@com.intellij.util.xml.SubTag (\"").append(tagName).append("\")");
          }
        } else {
          if (field.duplicateIndex >= 0) {
            sbAnnotations.append((JB_OFF ? "//" : "") + "\t@SubTag (value = \"").append(tagName).append("\", index = ").append(field.duplicateIndex - 1).append(")");
          } else if (field.clType == FieldDesc.BOOL) {
            sbAnnotations.append((JB_OFF ? "//" : "") + "\t@SubTag (value = \"").append(tagName).append("\", indicator = true)");
          } else if (!name.equals(field.name)) {
            sbAnnotations.append((JB_OFF ? "//" : "") + "\t@com.intellij.util.xml.SubTag (\"").append(tagName).append("\")");
          }
        }
        if (converterString != null) {
          sbAnnotations.append("\n").append(converterString);
        }
        if (NOT_COMPARE_MODE && td.type != TypeDesc.TypeEnum.GROUP_INTERFACE) {
          out.println("\t" + JDOC_OPEN);
          final String text;
          if (isList) {
            text = "the list of " + javaDocTagName;
          } else {
            text = "the value of the " + javaDocTagName;
          }
          out.println("\t" + JDOC_CONT + "Returns " + text + ".");
          printDocumentation(out, field.documentation, "\t" + JDOC_CONT);
          out.println("\t" + JDOC_CONT + "@return " + text + ".");
          out.println("\t" + JDOC_CLOSE);
        }
        out.println((JB_OFF ? "//" : "") + "\t@NotNull");
        if (td.type != TypeDesc.TypeEnum.GROUP_INTERFACE) {
          if (sbAnnotations.length() > 0) out.println(sbAnnotations);
          if (field.required) {
            out.println((JB_OFF ? "//" : "") + "\t@Required");
          }
        }
        out.print("\t");
        //out.print("public ");
        out.print(type);
        out.print(" ");
        out.print("get");
        out.print(Util.capitalize(name));
        out.println("();");

        final boolean genAddRemoveInsteadOfSet = true;
        if (!genAddRemoveInsteadOfSet || field.clType > 0) {
          if (field.clType == FieldDesc.SIMPLE) {
            if (NOT_COMPARE_MODE && td.type != TypeDesc.TypeEnum.GROUP_INTERFACE) {
              out.println("\t" + JDOC_OPEN);
              if (field.clType < 0) {
                out.println("\t" + JDOC_CONT + "Sets the list of " + javaDocTagName + ".");
              } else {
                out.println("\t" + JDOC_CONT + "Sets the value of the " + javaDocTagName + ".");
              }
              out.println("\t" + JDOC_CONT + "@param " + paramName + " the new value to set");
              out.println("\t" + JDOC_CLOSE);
              if (sbAnnotations.length() > 0) out.println(sbAnnotations);
            }
            out.print("\t");
            //out.print("public ");
            out.print("void set");
            out.print(Util.capitalize(name));
            out.print("(");
            if (field.required) {
              out.print((JB_OFF ? "" : "@NotNull "));
            }
            out.print(type);
            out.print(" ");
            out.print(paramName);
            out.println(");");
          }
        } else {
          if (NOT_COMPARE_MODE && td.type != TypeDesc.TypeEnum.GROUP_INTERFACE) {
            out.println("\t" + JDOC_OPEN);
            out.println("\t" + JDOC_CONT + "Adds new child to the list of " + javaDocTagName + ".");
            out.println("\t" + JDOC_CONT + "@return created child");
            out.println("\t" + JDOC_CLOSE);
            if (sbAnnotations.length() > 0) out.println(sbAnnotations);
          }
          out.print("\t");
          //out.print("public ");
          out.print(elementType + " add");
          out.print(Util.capitalize(field.elementName));
          out.println("();");
/*
                    if (!td.isGroupInterface) {
                        out.println("\t" + JDOC_OPEN);
                        out.println("\t" + JDOC_CONT + "Adds new child to the list of " + javaDocTagName + ".");
                        out.println("\t" + JDOC_CONT + "@param " + paramName + " the new child to add");
                        out.println("\t" + JDOC_CLOSE);
                        if (sbAnnotations.length() > 0) out.println(sbAnnotations);
                    }
                    out.print("\tpublic void add");
                    out.print(capitalize(fields[i].elementName));
                    out.print("(");
                    out.print(fields[i].elementType);
                    out.print(" ");
                    out.print(paramName);
                    out.println(");");

                    if (!td.isGroupInterface) {
                        out.println("\t" + JDOC_OPEN);
                        out.println("\t" + JDOC_CONT + "Removes the child from the list of " + javaDocTagName + ".");
                        out.println("\t" + JDOC_CONT + "@param " + paramName + " the new child to remove");
                        out.println("\t" + JDOC_CLOSE);
                        if (sbAnnotations.length() > 0) out.println(sbAnnotations);
                    }
                    out.print("\tpublic ");
                    out.print("void remove");
                    out.print(capitalize(fields[i].elementName));
                    out.print("(");
                    out.print(fields[i].elementType);
                    out.print(" ");
                    out.print(paramName);
                    out.println(");");
*/
        }

        out.println("");
        out.println("");
      }
      out.println("}");
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      try {
        out.close();
      } catch (Exception ex) {
      }
      fileManager.releaseOutputFile(outFile);
    }
  }

  private void generateSuper(FileManager fileManager, NamespaceDesc nsd, ModelDesc model, File outDir) {
    if (nsd.superClass == null || nsd.superClass.length() == 0) return;
    final String typeName = nsd.superClass.substring(nsd.superClass.lastIndexOf(".") + 1);
    final String typeQName = model.toJavaQualifiedTypeName("", nsd.superClass, false);
    String pkgName = typeQName.substring(0, typeQName.lastIndexOf('.'));
    File outFile = new File(outDir, toJavaFileName(typeQName));
    outFile = fileManager.getOutputFile(outFile);
    PrintWriter out = null;
    try {
      Util.log("Generating type: " + typeName + "..");
      out = new PrintWriter(new FileWriter(outFile));
      out.println("// Generated on " + new Date());
      out.println("// DTD/Schema  :    " + nsd.name);
      out.println("");
      if (pkgName != null) {
        out.println("package " + pkgName + ";");
      }
      out.println("");
      out.println("");
      out.println(JDOC_OPEN);
      out.println(JDOC_CONT + nsd.name + " base interface.");
      if (AUTHOR != null) out.println(JDOC_CONT + AUTHOR);
      out.println(JDOC_CLOSE);
      out.print("public interface " + typeName + " ");
      out.println("{");


      out.println("}");
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      try {
        if (out != null) {
          out.close();
        }
      } catch (Exception ex) {
      }
      fileManager.releaseOutputFile(outFile);
    }
  }

  private void generateHelper(FileManager fileManager, NamespaceDesc nsd, ModelDesc model, File outDir) {
    final Map<String, TypeDesc> jtMap = model.jtMap;
    final Map<String, NamespaceDesc > nsdMap = model.nsdMap;
    if (nsd.helperClass == null || nsd.helperClass.length() == 0) return;
    ArrayList<TypeDesc> jtList = new ArrayList<TypeDesc>();
    for (TypeDesc td : jtMap.values()) {
      if (td.type != TypeDesc.TypeEnum.CLASS) continue;
//            if (!nsd.name.equals(td.xsNamespace)) {
//                continue;
//            }
      jtList.add(td);
    }
    if (jtList.size() == 0) return;

    String typeName = nsd.helperClass.substring(nsd.helperClass.lastIndexOf(".") + 1);
    final String typeQName = model.toJavaQualifiedTypeName("", nsd.helperClass, false);
    String pkgName = typeQName.substring(0, typeQName.lastIndexOf('.'));
    File outFile = new File(outDir, toJavaFileName(typeQName));
    outFile = fileManager.getOutputFile(outFile);
    PrintWriter out = null;
    try {
      Util.log("Generating type: " + typeName + "..");
      out = new PrintWriter(new FileWriter(outFile));
      out.println("// Generated on " + new Date());
      out.println("// DTD/Schema  :    " + nsd.name);
      out.println("");
      if (pkgName != null) {
        out.println("package " + pkgName + ";");
      }
      out.println("");
      out.println("");
      out.println(JDOC_OPEN);
      out.println(JDOC_CONT + nsd.name + " helper class.");
      if (AUTHOR != null) out.println(JDOC_CONT + AUTHOR);
      out.println(JDOC_CLOSE);
      out.print("public class " + typeName + " ");
      out.println("{");
      out.println("");

// child index method
//            class Holder implements Comparable {
//                String str;
//                int index;
//
//                public Holder(String str, int index) {
//                    this.str = str;
//                    this.index = index;
//                }
//
//                public int compareTo(Object o) {
//                    return str.compareTo(o==null?null:((Holder)o).str);
//                }
//            };
//            ArrayList holders = new ArrayList();
//            for (Iterator it = jtList.iterator(); it.hasNext();) {
//                TypeDesc td = (TypeDesc) it.next();
//                NamespaceDesc tdnsd = (NamespaceDesc) nsdMap.get(td.xsNamespace);
//                String qname = toJavaQualifiedTypeName(tdnsd, td.name);
//                ArrayList fields = new ArrayList(td.fdMap.values());
//                Collections.sort(fields, new Comparator() {
//                    public int compare(Object o1, Object o2) {
//                        return ((FieldDesc) o1).realIndex - ((FieldDesc) o2).realIndex;
//                    }
//                });
//                for (Iterator it2 = fields.iterator(); it2.hasNext();) {
//                    FieldDesc fd = (FieldDesc) it2.next();
//                    Holder h = new Holder(qname+":"+fd.tagName, fd.realIndex);
//                    holders.add(h);
//                }
//            }
//            Collections.sort(holders);
//            out.println("\tprivate static String[] parentChildTags = new String[] {");
//            int idx = 0;
//            for (Iterator it = holders.iterator(); it.hasNext(); idx++) {
//                Holder holder = (Holder) it.next();
//                if (idx % 4 == 0) out.print("\n\t\t");
//                out.print("\""+holder.str+"\"");
//                if (idx < holders.size()-1) out.print(",");
//            }
//            out.println("\n\t};");
//            out.println("\tprivate static int[] parentChildIndices = new int[] {");
//            idx = 0;
//            for (Iterator it = holders.iterator(); it.hasNext(); idx++) {
//                Holder holder = (Holder) it.next();
//                if (idx % 24 == 0) out.print("\n\t\t");
//                out.print(holder.index);
//                if (idx < holders.size()-1) out.print(",");
//            }
//            out.println("\n\t};");
//            out.println();
//            out.println("\tpublic static int getChildIndex(String parentTag, String childTag) {");
//            out.println("\t\tint idx = java.util.Arrays.binarySearch(parentChildTags, parentTag+\":\"+childTag);");
//            out.println("\t\treturn idx < 0? -1: parentChildIndices[idx];");
//            out.println("\t}");


      out.println("\tprivate interface GetName { String getName(Object o); }");
      out.println("\tprivate static java.util.HashMap<Class, GetName> nameMap = new java.util.HashMap();");
      out.println("\tstatic {");

      for (TypeDesc td : jtList) {
        ArrayList<FieldDesc> fields = new ArrayList<FieldDesc>(td.fdMap.values());
        Collections.sort(fields, new Comparator<FieldDesc>() {
          public int compare(FieldDesc o1, FieldDesc o2) {
            return o1.realIndex - o2.realIndex;
          }
        });
        int guessPriority = 0;
        FieldDesc guessedField = null;
        for (FieldDesc fd : fields) {
          if (fd.clType == FieldDesc.STR || fd.clType == FieldDesc.SIMPLE || fd.clType == FieldDesc.ATTR) {
            if (fd.name.equals("name") && guessPriority < 10) {
              guessPriority = 10;
              guessedField = fd;
            } else if (fd.name.endsWith("Name")) {
              if ((fd.name.endsWith(Util.decapitalize(td.name + "Name")) || fd.realIndex < 2) && guessPriority < 10) {
                guessPriority = 10;
                guessedField = fd;
              } else if (fd.name.endsWith(Util.decapitalize("DisplayName")) && guessPriority < 5) {
                guessPriority = 5;
                guessedField = fd;
              } else if (guessPriority < 3) {
                guessPriority = 3;
                guessedField = fd;
              }
            } else if (fd.name.equals("value") && guessPriority < 1) {
              guessPriority = 1;
              guessedField = fd;
            }
          } else
          if ((fd.clType == -FieldDesc.OBJ || fd.clType == -FieldDesc.STR) && fd.name.endsWith("displayNames") && guessPriority < 5) {
            guessPriority = 5;
            guessedField = fd;
          }
        }
        out.println();
        String qname = model.getNSDPrefix(td) + td.name;
        String tdNameString = "\"" + toPresentationName(td.name) + "\"";
        out.println("\t\tnameMap.put(" + qname + ".class, new GetName() {");
        out.println("\t\t\tpublic String getName(Object o) {");
        if (guessedField != null) {
          out.println("\t\t\t\t" + qname + " my = (" + qname + ") o;");
          String getter = "my.get" + Util.capitalize(guessedField.name) + "()";
          if (guessedField.clType > 0) {
            out.println("\t\t\t\tString s = o==null? null:" + getter +
                    (guessedField.clType == FieldDesc.STR || guessedField.clType == FieldDesc.ATTR ? ".getValue();" : ";"));
            out.println("\t\t\t\treturn s==null?" + tdNameString + ":s;");
          } else {
            out.println("\t\t\t\treturn (o!=null && " + getter + "!=null && " + getter + ".size()>0)?");
            out.println("\t\t\t\t\tgetPresentationName(" + getter + ".get(0), null):" + tdNameString + ";");
          }
        } else {
          out.println("\t\t\t\treturn " + tdNameString + ";");
        }
        out.println("\t\t\t}");
        out.println("\t\t});");
      }
      out.println("\t}");

      out.println("\tpublic static String getPresentationName(Object o, String def) {");
      out.println("\t\tGetName g = o!=null? nameMap.get(o.getClass().getInterfaces()[0]):null;");
      out.println("\t\treturn g != null?g.getName(o):def;");
      out.println("\t}");
      out.println("}");
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      try {
        out.close();
      } catch (Exception ex) {
      }
      fileManager.releaseOutputFile(outFile);
    }
  }

  public static void printDocumentation(PrintWriter out, String str, String prefix) {
    if (str == null) return;
    StringTokenizer st = new StringTokenizer(str, "\n\r");
    while (st.hasMoreTokens()) {
      String line = prefix + st.nextToken();
      out.println(line);
    }
  }

  public static String toPresentationName(String typeName) {
    StringBuffer sb = new StringBuffer(typeName.length() + 10);
    boolean prevUp = true;
    for (int i = 0; i < typeName.length(); i++) {
      char c = typeName.charAt(i);
      if (Character.isUpperCase(c) && !prevUp) {
        sb.append(' ');
      }
      sb.append(c);
      prevUp = Character.isUpperCase(c);
    }
    return sb.toString();
  }

  private static String toJavaFileName(String typeName) {
    return typeName.replace('.', File.separatorChar) + ".java";
  }

  public static String toJavaIdName(String javaFieldName) {
    if (Util.RESERVED_NAMES_MAP.containsKey(javaFieldName)) {
      javaFieldName += "_";
    }
    return javaFieldName;
  }


  public void setAuthor(String author) {
    AUTHOR = "@author: " + author;
  }
}
