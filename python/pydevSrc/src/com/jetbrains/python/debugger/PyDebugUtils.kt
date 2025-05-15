// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

interface AbstractPolicy


fun interface PolicyListener {
  fun valuesPolicyUpdated()
}


enum class ValuesPolicy : AbstractPolicy {
  SYNC,
  ASYNC,
  ON_DEMAND
}

enum class QuotingPolicy : AbstractPolicy {
  SINGLE,
  DOUBLE,
  NONE
}

object NodeTypes {
  const val ARRAY_NODE_TYPE: String = "array"
  const val DICT_NODE_TYPE: String = "dict"
  const val LIST_NODE_TYPE: String = "list"
  const val TUPLE_NODE_TYPE: String = "tuple"
  const val SET_NODE_TYPE: String = "set"
  const val MATRIX_NODE_TYPE: String = "matrix"
  const val NDARRAY_NODE_TYPE: String = "ndarray"
  const val RECARRAY_NODE_TYPE: String = "recarray"
  const val EAGER_TENSOR_NODE_TYPE: String = "EagerTensor"
  const val RESOURCE_VARIABLE_NODE_TYPE: String = "ResourceVariable"
  const val SPARSE_TENSOR_NODE_TYPE: String = "SparseTensor"
  const val TENSOR_NODE_TYPE: String = "Tensor"
  const val IMAGE_NODE_TYPE: String = "Image"
  const val PNG_IMAGE_NODE_TYPE: String = "PngImageFile"
  const val JPEG_IMAGE_NODE_TYPE: String = "JpegImageFile"
  const val FIGURE_NODE_TYPE: String = "Figure"
  const val DATA_FRAME_NODE_TYPE: String = "DataFrame"
  const val SERIES_NODE_TYPE: String = "Series"
  const val GEO_DATA_FRAME_NODE_TYPE: String = "GeoDataFrame"
  const val GEO_SERIES_NODE_TYPE: String = "GeoSeries"
  const val DATASET_NODE_TYPE: String = "Dataset"
  const val NESTED_ORDERED_DICT_NODE_TYPE: String = "NestedOrderedDict"
  const val DATASET_DICT_NODE_TYPE: String = "DatasetDict"
}

fun getQuotingString(policy: QuotingPolicy, value: String): String =
  when (policy) {
    QuotingPolicy.SINGLE -> value
    QuotingPolicy.DOUBLE -> value.replace("'", "\"")
    QuotingPolicy.NONE -> value.replace("'", "")
  }