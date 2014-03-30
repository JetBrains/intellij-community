/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.rest;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;

/**
 * User : catherine
 */
public class RestUtil {
  private RestUtil() {}

  public static String[] SPHINX_DIRECTIVES = new String[] {
    "module::" ,  "automodule::" ,  "autoclass::" ,  "toctree::" ,  "glossary::" ,  "code-block::", "versionadded::",
    "versionchanged::", "deprecated::", "seealso::", "centered::", "hlist::", "index::", "productionlist::", "highlight::",
    "literalinclude::", "sectionauthor::", "codeauthor::", "only::", "tabularcolumns::", "py:function::", "default-domain::",
    "py:module::", "py:currentmodule::", "py:data::", "py:exception::", "py:function::", "py:class::", "py:attribute::",
    "py:method::", "py:staticmethod::", "py:classmethod::", "c:function::", "c:member::", "c:macro::", "c:type::", "c:var::",
    "cpp:class::", "cpp:function::", "cpp:member::", "cpp:type::", "option::", "envvar::", "program::", "describe::", "object::",
    "js:function::", "js:class::", "js:data::", "js:attribute::", "rst:directive::", "rst:role::"
  };


  public static final Set<String> PREDEFINED_ROLES = Sets.newHashSet();
  public static final Set<String> SPHINX_ROLES = Sets.newHashSet();
  private static final Map<String, String[]> DIRECTIVES = Maps.newHashMap();

  static {
    PREDEFINED_ROLES.add(":emphasis:");
    PREDEFINED_ROLES.add(":literal:");
    PREDEFINED_ROLES.add(":pep-reference:");
    PREDEFINED_ROLES.add(":PEP:");
    PREDEFINED_ROLES.add(":rfc-reference:");
    PREDEFINED_ROLES.add(":RFC:");
    PREDEFINED_ROLES.add(":strong:");
    PREDEFINED_ROLES.add(":subscript:");
    PREDEFINED_ROLES.add(":sub:");
    PREDEFINED_ROLES.add(":superscript:");
    PREDEFINED_ROLES.add(":sup:");
    PREDEFINED_ROLES.add(":title-reference:");
    PREDEFINED_ROLES.add(":title:");
    PREDEFINED_ROLES.add(":t:");
    PREDEFINED_ROLES.add(":raw:");

    SPHINX_ROLES.add(":py:mod:");
    SPHINX_ROLES.add(":py:func:");
    SPHINX_ROLES.add(":py:data:");
    SPHINX_ROLES.add(":py:const:");
    SPHINX_ROLES.add(":py:class:");
    SPHINX_ROLES.add(":py:meth:");
    SPHINX_ROLES.add(":py:attr:");
    SPHINX_ROLES.add(":py:exc:");
    SPHINX_ROLES.add(":py:obj:");
    SPHINX_ROLES.add(":ref:");
    SPHINX_ROLES.add(":doc:");
    SPHINX_ROLES.add(":download:");
    SPHINX_ROLES.add(":envvar:");
    SPHINX_ROLES.add(":token:");
    SPHINX_ROLES.add(":keyword:");
    SPHINX_ROLES.add(":option:");
    SPHINX_ROLES.add(":term:");
    SPHINX_ROLES.add(":abbr:");
    SPHINX_ROLES.add(":command:");
    SPHINX_ROLES.add(":dfn:");
    SPHINX_ROLES.add(":file:");
    SPHINX_ROLES.add(":guilabel:");
    SPHINX_ROLES.add(":kbd:");
    SPHINX_ROLES.add(":mailheader:");
    SPHINX_ROLES.add(":makevar:");
    SPHINX_ROLES.add(":manpage:");
    SPHINX_ROLES.add(":menuselection:");
    SPHINX_ROLES.add(":mimetype:");
    SPHINX_ROLES.add(":newsgroup:");
    SPHINX_ROLES.add(":program:");
    SPHINX_ROLES.add(":regexp:");
    SPHINX_ROLES.add(":samp:");
    SPHINX_ROLES.add(":pep:");
    SPHINX_ROLES.add(":rfc:");

    DIRECTIVES.put("attention::", new String[] {});
    DIRECTIVES.put("caution::", new String[] {});
    DIRECTIVES.put("danger::", new String[] {});
    DIRECTIVES.put("error::", new String[] {});
    DIRECTIVES.put("hint::", new String[] {});
    DIRECTIVES.put("important::", new String[] {});
    DIRECTIVES.put("note::", new String[] {});
    DIRECTIVES.put("tip::", new String[] {});
    DIRECTIVES.put("warning::", new String[] {});
    DIRECTIVES.put("admonition::", new String[] {":class:"});
    DIRECTIVES.put("image::", new String[] {":alt:", ":height:", ":width:", ":scale:", ":align:", ":target:", ":class:"});
    DIRECTIVES.put("figure::", new String[] {":alt:", ":height:", ":width:", ":scale:", ":align:", ":target:", ":class:", ":figwidth:", ":figclass:"});
    DIRECTIVES.put("topic::", new String[] {":class:"});
    DIRECTIVES.put("sidebar::", new String[] {":subtitle:", ":class:"});
    DIRECTIVES.put("line-block::", new String[] {":class:"});
    DIRECTIVES.put("parsed-literal::", new String[] {":class:"});
    DIRECTIVES.put("rubric::", new String[] {":class:"});
    DIRECTIVES.put("epigraph::", new String[] {});
    DIRECTIVES.put("highlights::", new String[] {});
    DIRECTIVES.put("pull-quote::", new String[] {});
    DIRECTIVES.put("compound::", new String[] {":class:"});
    DIRECTIVES.put("container::", new String[] {});
    DIRECTIVES.put("table::", new String[] {":class:"});
    DIRECTIVES.put("csv-table::", new String[] {":class:", ":widths:", ":header-rows:", ":stub-columns:", ":header:", ":file:", ":url:", ":encoding:",
                                              ":delim:", ":quote:", ":keepspace:", ":escape:"});
    DIRECTIVES.put("list-table::", new String[] {":class:", ":widths:", ":header-rows:", ":stub-columns:"});
    DIRECTIVES.put("contents::", new String[] {":class:", ":depth:", ":local:", ":backlinks:"});
    DIRECTIVES.put("sectnum::", new String[] {":depth:", ":prefix:", ":suffix:", ":start:"});
    DIRECTIVES.put("section-autonumbering::", new String[] {":depth:", ":prefix:", ":suffix:", ":start:"});
    DIRECTIVES.put("header::", new String[] {});
    DIRECTIVES.put("footer::", new String[] {});
    DIRECTIVES.put("target-notes::", new String[] {"class"});

    DIRECTIVES.put("footnotes::", new String[] {});
    DIRECTIVES.put("citations::", new String[] {});
    DIRECTIVES.put("meta::", new String[] {});

    DIRECTIVES.put("replace::", new String[] {});
    DIRECTIVES.put("unicode::", new String[] {":ltrim:", ":rtrim:", ":trim:"});
    DIRECTIVES.put("date::", new String[] {});
    DIRECTIVES.put("include::", new String[] {":start-line:", ":end-line:", ":start-after:", ":end-before:", ":literal:", ":encoding:", ":tab-width:"});
    DIRECTIVES.put("raw::", new String[] {":file:", ":url:", ":encoding:"});
    DIRECTIVES.put("class::", new String[] {});
    DIRECTIVES.put("role::", new String[] {":class:", ":format:"});
    DIRECTIVES.put("default-role::", new String[] {});
    DIRECTIVES.put("title::", new String[] {});
    DIRECTIVES.put("restructuredtext-test-directive::", new String[] {});
  }

  static public String[] getDirectiveOptions(String directive) {
    if (DIRECTIVES.containsKey(directive))
      return DIRECTIVES.get(directive);
    return new String[]{};
  }

  static public Set<String> getDirectives() {
    return DIRECTIVES.keySet();
  }
}
