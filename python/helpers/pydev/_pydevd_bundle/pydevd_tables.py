#  Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


from _pydevd_bundle import pydevd_vars
from _pydevd_bundle.pydevd_constants import NEXT_VALUE_SEPARATOR
from _pydevd_bundle.pydevd_xml import ExceptionOnEvaluate
from _pydevd_bundle.tables.images.pydevd_image_loader import load_image_chunk


class TableCommandType:
    DF_INFO = "DF_INFO"
    SLICE = "SLICE"
    SLICE_CSV = "SLICE_CSV"
    DESCRIBE = "DF_DESCRIBE"
    VISUALIZATION_DATA = "VISUALIZATION_DATA"
    IMAGE_START_CHUNK_LOAD = "IMAGE_START_CHUNK_LOAD"
    IMAGE_CHUNK_LOAD = "IMAGE_CHUNK_LOAD"


def is_error_on_eval(val):
    try:
        # This should be faster than isinstance (but we have to protect against not
        # having a '__class__' attribute).
        is_exception_on_eval = val.__class__ == ExceptionOnEvaluate
    except:
        is_exception_on_eval = False
    return is_exception_on_eval

def exec_image_table_command(init_command, command_type, offset, image_id, f_globals, f_locals):
    table = pydevd_vars.eval_in_context(init_command, f_globals, f_locals)
    is_exception_on_eval = is_error_on_eval(table)
    if is_exception_on_eval:
        return False, table.result

    image_provider = __get_image_provider(table)
    if not image_provider:
        raise RuntimeError('No image provider for: {}'.format(type(table)))

    if command_type == TableCommandType.IMAGE_START_CHUNK_LOAD:
        return True, image_provider.create_image(table)

    return True, load_image_chunk(offset, image_id)


def exec_table_command(init_command, command_type, start_index, end_index, format, f_globals,
                       f_locals):
    table = pydevd_vars.eval_in_context(init_command, f_globals, f_locals)
    is_exception_on_eval = is_error_on_eval(table)
    if is_exception_on_eval:
        return False, table.result

    table_provider = __get_table_provider(table)
    if not table_provider:
        raise RuntimeError('No table data provider for: {}'.format(type(table)))

    res = []
    if command_type == TableCommandType.DF_INFO:
        res.append(table_provider.get_type(table))
        res.append(NEXT_VALUE_SEPARATOR)
        res.append(table_provider.get_shape(table))
        res.append(NEXT_VALUE_SEPARATOR)
        res.append(table_provider.get_head(table))
        res.append(NEXT_VALUE_SEPARATOR)
        res.append(table_provider.get_column_types(table))

    elif command_type == TableCommandType.DESCRIBE:
        res.append(table_provider.get_column_descriptions(table))

    elif command_type == TableCommandType.VISUALIZATION_DATA:
        res.append(table_provider.get_value_occurrences_count(table))
        res.append(NEXT_VALUE_SEPARATOR)

    elif command_type == TableCommandType.SLICE:
        res.append(table_provider.get_data(table, False, start_index, end_index, format))
    elif command_type == TableCommandType.SLICE_CSV:
        res.append(table_provider.get_data(table, True, start_index, end_index, format))

    return True, ''.join(res)


def __get_type_name(table):
    table_data_type = type(table)
    table_data_type_name = '{}.{}'.format(table_data_type.__module__, table_data_type.__name__)
    return table_data_type_name


# noinspection PyUnresolvedReferences
def __get_table_provider(output):
    # type: (str) -> Any
    type_qualified_name = __get_type_name(output)
    numpy_based_type_qualified_names = ['tensorflow.python.framework.ops.EagerTensor',
                                        'tensorflow.python.ops.resource_variable_ops.ResourceVariable',
                                        'tensorflow.python.framework.sparse_tensor.SparseTensor',
                                        'torch.Tensor']
    table_provider = None
    if type_qualified_name in ['pandas.core.frame.DataFrame',
                               'pandas.core.series.Series',
                               'geopandas.geoseries.GeoSeries',
                               'geopandas.geodataframe.GeoDataFrame',
                               'pandera.typing.pandas.DataFrame']:
        import _pydevd_bundle.tables.pydevd_pandas as table_provider
    # dict is needed for sort commands
    elif type_qualified_name == 'builtins.dict':
        table_type_name = __get_type_name(output['data'])
        if table_type_name in numpy_based_type_qualified_names:
            import _pydevd_bundle.tables.pydevd_numpy_based as table_provider
        else:
            import _pydevd_bundle.tables.pydevd_numpy as table_provider
    elif type_qualified_name == 'numpy.ndarray' or type_qualified_name == 'numpy.rec.recarray':
        import _pydevd_bundle.tables.pydevd_numpy as table_provider
    elif type_qualified_name in numpy_based_type_qualified_names:
        import _pydevd_bundle.tables.pydevd_numpy_based as table_provider
    elif type_qualified_name.startswith('polars') and (
            type_qualified_name.endswith('DataFrame')
            or type_qualified_name.endswith('Series')):
        import _pydevd_bundle.tables.pydevd_polars as table_provider
    elif type_qualified_name == 'datasets.arrow_dataset.Dataset':
        import _pydevd_bundle.tables.pydevd_dataset as table_provider

    return table_provider


# noinspection PyUnresolvedReferences
def __get_image_provider(output):
    # type: (str) -> Any
    type_qualified_name = __get_type_name(output)
    numpy_based_type_qualified_names = ['tensorflow.python.framework.ops.EagerTensor',
                                        'tensorflow.python.ops.resource_variable_ops.ResourceVariable',
                                        'tensorflow.python.framework.sparse_tensor.SparseTensor',
                                        'torch.Tensor']
    image_provider = None
    if type_qualified_name == 'builtins.dict':
        table_type_name = __get_type_name(output['data'])
        if table_type_name in numpy_based_type_qualified_names:
            import _pydevd_bundle.tables.images.pydevd_numpy_based_image as image_provider
        else:
            import _pydevd_bundle.tables.images.pydevd_numpy_image as image_provider
    elif type_qualified_name in numpy_based_type_qualified_names:
        import _pydevd_bundle.tables.images.pydevd_numpy_based_image as image_provider
    elif type_qualified_name == 'numpy.ndarray':
        import _pydevd_bundle.tables.images.pydevd_numpy_image as image_provider
    elif type_qualified_name in ['PIL.Image.Image', 'PIL.PngImagePlugin.PngImageFile', 'PIL.JpegImagePlugin.JpegImageFile']:
        import _pydevd_bundle.tables.images.pydevd_pillow_image as image_provider
    elif type_qualified_name in ['matplotlib.figure.Figure', 'plotly.graph_objs._figure.Figure']:
        import _pydevd_bundle.tables.images.pydevd_matplotlib_image as image_provider

    return image_provider
