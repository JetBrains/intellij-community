from collections import namedtuple
from typing import Any

from django.db.backends.base.introspection import BaseDatabaseIntrospection
from django.db.backends.mysql.base import DatabaseWrapper

FieldInfo: Any
InfoLine = namedtuple(
    "InfoLine",
    [
        "col_name",
        "data_type",
        "max_len",
        "num_prec",
        "num_scale",
        "extra",
        "column_default",
        "collation",
        "is_unsigned",
    ],
)

class DatabaseIntrospection(BaseDatabaseIntrospection):
    connection: DatabaseWrapper
    data_types_reverse: Any
