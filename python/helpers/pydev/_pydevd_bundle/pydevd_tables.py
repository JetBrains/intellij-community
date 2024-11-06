#  Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


from _pydevd_bundle import pydevd_vars
from _pydevd_bundle.pydevd_constants import NEXT_VALUE_SEPARATOR
from _pydevd_bundle.pydevd_xml import ExceptionOnEvaluate


class TableCommandType:
    DF_INFO = "DF_INFO"
    SLICE = "SLICE"
    SLICE_CSV = "SLICE_CSV"
    DESCRIBE = "DF_DESCRIBE"
    VISUALIZATION_DATA = "VISUALIZATION_DATA"


def is_error_on_eval(val):
    try:
        # This should be faster than isinstance (but we have to protect against not
        # having a '__class__' attribute).
        is_exception_on_eval = val.__class__ == ExceptionOnEvaluate
    except:
        is_exception_on_eval = False
    return is_exception_on_eval


def exec_table_command(init_command, command_type, start_index, end_index, format, f_globals,
                       f_locals):
    # type: (str, str, [int, None], [int, None], [str, None], dict, dict) -> (bool, str)
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


# noinspection PyUnresolvedReferences
def __get_table_provider(output):
    # type: (str) -> Any
    output_type = type(output)

    table_provider = None
    type_qualified_name = '{}.{}'.format(output_type.__module__, output_type.__name__)
    numpy_based_type_qualified_names = ['tensorflow.python.framework.ops.EagerTensor',
                                        'tensorflow.python.ops.resource_variable_ops.ResourceVariable',
                                        'tensorflow.python.framework.sparse_tensor.SparseTensor',
                                        'torch.Tensor']
    if type_qualified_name in ['pandas.core.frame.DataFrame',
                               'pandas.core.series.Series',
                               'geopandas.geoseries.GeoSeries',
                               'geopandas.geodataframe.GeoDataFrame',
                               'pandera.typing.pandas.DataFrame']:
        import _pydevd_bundle.tables.pydevd_pandas as table_provider
    # dict is needed for sort commands
    elif type_qualified_name == 'builtins.dict':
        table_type = '{}.{}'.format(type(output['data']).__module__, type(output['data']).__name__)
        if table_type in numpy_based_type_qualified_names:
            import _pydevd_bundle.tables.pydevd_numpy_based as table_provider
        else:
            import _pydevd_bundle.tables.pydevd_numpy as table_provider
    elif type_qualified_name == 'numpy.ndarray':
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
