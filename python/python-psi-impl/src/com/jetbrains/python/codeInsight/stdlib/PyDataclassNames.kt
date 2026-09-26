// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.stdlib

import com.jetbrains.python.PyNames

object PyDataclassNames {
  object Dataclasses {
    const val DATACLASSES_MISSING: String = "dataclasses.MISSING"
    const val DATACLASSES_INITVAR: String = "dataclasses.InitVar"
    const val DATACLASSES_FIELDS: String = "dataclasses.fields"
    const val DATACLASSES_ASDICT: String = "dataclasses.asdict"
    const val DATACLASSES_FIELD: String = "dataclasses.field"
    const val DATACLASSES_REPLACE: String = "dataclasses.replace"
    const val DATACLASSES_KW_ONLY: String = "dataclasses.KW_ONLY"
    const val DUNDER_POST_INIT: String = "__post_init__"
    const val DUNDER_SLOTS: String = PyNames.SLOTS
    const val DUNDER_MATCH_ARGS: String = PyNames.MATCH_ARGS

    /** @see com.jetbrains.python.codeInsight.stdlib.PyDataclassResolver.omittedDefaultQualifiedNames */
    val OMITTED_DEFAULTS: Set<String> = setOf(DATACLASSES_MISSING)
    val DECORATOR_PARAMETERS: List<String> = listOf("init", "repr", "eq", "order", "unsafe_hash", "frozen", "match_args", "kw_only", "slots")
    val HELPER_FUNCTIONS: Set<String> = setOf(DATACLASSES_FIELDS, DATACLASSES_ASDICT, "dataclasses.astuple", DATACLASSES_REPLACE)
  }

  object Attrs {
    val ATTRS_NOTHING: Set<String> = setOf("attr.NOTHING", "attrs.NOTHING")
    val ATTRS_FACTORY: Set<String> = setOf("attr.Factory", "attrs.Factory")
    val ATTRS_ASSOC: Set<String> = setOf("attr.assoc", "attrs.assoc")
    val ATTRS_EVOLVE: Set<String> = setOf("attr.evolve", "attrs.evolve")
    val ATTRS_FROZEN: Set<String> = setOf("attr.frozen", "attrs.frozen")
    const val DUNDER_POST_INIT: String = "__attrs_post_init__"
    const val DUNDER_ATTRS: String = "__attrs_attrs__"
    val DECORATOR_PARAMETERS: List<String> = listOf(
      "these",
      "repr_ns",
      "repr",
      "cmp",
      "hash",
      "init",
      "slots",
      "frozen",
      "weakref_slot",
      "str",
      "auto_attribs",
      "kw_only",
      "cache_hash",
      "auto_exc",
      "eq",
      "order",
      "match_args",
    )
    val FIELD_FUNCTIONS: Set<String> = setOf(
      "attr.ib",
      "attr.attr",
      "attr.attrib",
      "attr.field",
      "attrs.field",
    )
    val INSTANCE_HELPER_FUNCTIONS: Set<String> = setOf(
      "attr.asdict",
      "attr.astuple",
      "attr.assoc",
      "attr.evolve",
      "attrs.asdict",
      "attrs.astuple",
      "attrs.assoc",
      "attrs.evolve",
    )
    val CLASS_HELPERS_FUNCTIONS: Set<String> = setOf(
      "attr.fields",
      "attr.fields_dict",
      "attrs.fields",
      "attrs.fields_dict",
    )
  }

  object DataclassTransform {
    const val DATACLASS_TRANSFORM_NAME: String = "dataclass_transform"

    val DECORATOR_OR_CLASS_PARAMETERS: Set<String> = setOf(
      "init",
      "eq",
      "order",
      "unsafe_hash",
      "frozen",
      "match_args",
      "kw_only",
      "slots",
    )

    val FIELD_SPECIFIER_PARAMETERS: Set<String> = setOf(
      "init",
      "default",
      "default_factory",
      "factory",
      "kw_only",
      "alias",
    )
  }
}
