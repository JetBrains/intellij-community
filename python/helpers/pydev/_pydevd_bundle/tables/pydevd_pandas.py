#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import io


def get_type(table) -> str:
    return str(type(table))


def get_shape(table) -> str:
    return str(table.shape[0])


def get_head(table, max_cols) -> str:
    return repr(table.head().to_html(notebook=True, max_cols=max_cols))


def get_column_types(table) -> str:
    with io.StringIO() as output:
        print(table.index.dtype, *[str(t) for t in table.dtypes],
              file=output)
        return output.getvalue()
