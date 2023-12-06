from typing import Any, overload
from typing_extensions import Self, final

ATTR_CASE: int
CASE_LOWER: int
CASE_NATURAL: int
CASE_UPPER: int
PARAM_FILE: int
QUOTED_LITERAL_REPLACEMENT_OFF: int
QUOTED_LITERAL_REPLACEMENT_ON: int
SQL_API_SQLROWCOUNT: int
SQL_ATTR_AUTOCOMMIT: int
SQL_ATTR_CURRENT_SCHEMA: int
SQL_ATTR_CURSOR_TYPE: int
SQL_ATTR_INFO_ACCTSTR: int
SQL_ATTR_INFO_APPLNAME: int
SQL_ATTR_INFO_PROGRAMNAME: int
SQL_ATTR_INFO_USERID: int
SQL_ATTR_INFO_WRKSTNNAME: int
SQL_ATTR_PARAMSET_SIZE: int
SQL_ATTR_PARAM_BIND_TYPE: int
SQL_ATTR_QUERY_TIMEOUT: int
SQL_ATTR_ROWCOUNT_PREFETCH: int
SQL_ATTR_TRUSTED_CONTEXT_PASSWORD: int
SQL_ATTR_TRUSTED_CONTEXT_USERID: int
SQL_ATTR_USE_TRUSTED_CONTEXT: int
SQL_ATTR_XML_DECLARATION: int
SQL_AUTOCOMMIT_OFF: int
SQL_AUTOCOMMIT_ON: int
SQL_BIGINT: int
SQL_BINARY: int
SQL_BIT: int
SQL_BLOB: int
SQL_BLOB_LOCATOR: int
SQL_BOOLEAN: int
SQL_CHAR: int
SQL_CLOB: int
SQL_CLOB_LOCATOR: int
SQL_CURSOR_DYNAMIC: int
SQL_CURSOR_FORWARD_ONLY: int
SQL_CURSOR_KEYSET_DRIVEN: int
SQL_CURSOR_STATIC: int
SQL_DBCLOB: int
SQL_DBCLOB_LOCATOR: int
SQL_DBMS_NAME: int
SQL_DBMS_VER: int
SQL_DECFLOAT: int
SQL_DECIMAL: int
SQL_DOUBLE: int
SQL_FALSE: int
SQL_FLOAT: int
SQL_GRAPHIC: int
SQL_INDEX_CLUSTERED: int
SQL_INDEX_OTHER: int
SQL_INTEGER: int
SQL_LONGVARBINARY: int
SQL_LONGVARCHAR: int
SQL_LONGVARGRAPHIC: int
SQL_NUMERIC: int
SQL_PARAM_BIND_BY_COLUMN: int
SQL_PARAM_INPUT: int
SQL_PARAM_INPUT_OUTPUT: int
SQL_PARAM_OUTPUT: int
SQL_REAL: int
SQL_ROWCOUNT_PREFETCH_OFF: int
SQL_ROWCOUNT_PREFETCH_ON: int
SQL_SMALLINT: int
SQL_TABLE_STAT: int
SQL_TINYINT: int
SQL_TRUE: int
SQL_TYPE_DATE: int
SQL_TYPE_TIME: int
SQL_TYPE_TIMESTAMP: int
SQL_VARBINARY: int
SQL_VARCHAR: int
SQL_VARGRAPHIC: int
SQL_WCHAR: int
SQL_WLONGVARCHAR: int
SQL_WVARCHAR: int
SQL_XML: int
USE_WCHAR: int
WCHAR_NO: int
WCHAR_YES: int

# TODO: SQL_ATTR_TXN_ISOLATION: int
SQL_ATTR_ACCESS_MODE: int
SQL_ATTR_ALLOW_INTERLEAVED_GETDATA: int
SQL_ATTR_ANSI_APP: int
SQL_ATTR_APPEND_FOR_FETCH_ONLY: int
SQL_ATTR_APP_USES_LOB_LOCATOR: int
SQL_ATTR_ASYNC_ENABLE: int
SQL_ATTR_AUTO_IPD: int
SQL_ATTR_CACHE_USRLIBL: int
SQL_ATTR_CLIENT_APPLCOMPAT: int
SQL_ATTR_CLIENT_CODEPAGE: int
SQL_ATTR_COLUMNWISE_MRI: int
SQL_ATTR_COMMITONEOF: int
SQL_ATTR_CONCURRENT_ACCESS_RESOLUTION: int
SQL_ATTR_CONFIG_KEYWORDS_ARRAY_SIZE: int
SQL_ATTR_CONFIG_KEYWORDS_MAXLEN: int
SQL_ATTR_CONNECTION_DEAD: int
SQL_ATTR_CONNECTTYPE: int
SQL_ATTR_CONNECT_NODE: int
SQL_ATTR_CONNECT_PASSIVE: int
SQL_ATTR_CONN_CONTEXT: int
SQL_ATTR_CURRENT_CATALOG: int
SQL_ATTR_CURRENT_IMPLICIT_XMLPARSE_OPTION: int
SQL_ATTR_CURRENT_PACKAGE_PATH: int
SQL_ATTR_CURRENT_PACKAGE_SET: int
SQL_ATTR_DATE_FMT: int
SQL_ATTR_DATE_SEP: int
SQL_ATTR_DB2EXPLAIN: int
SQL_ATTR_DB2_APPLICATION_HANDLE: int
SQL_ATTR_DB2_APPLICATION_ID: int
SQL_ATTR_DB2_SQLERRP: int
SQL_ATTR_DECFLOAT_ROUNDING_MODE: int
SQL_ATTR_DECIMAL_SEP: int
SQL_ATTR_DESCRIBE_CALL: int
SQL_ATTR_DESCRIBE_OUTPUT_LEVEL: int
SQL_ATTR_DETECT_READ_ONLY_TXN: int
SQL_ATTR_ENLIST_IN_DTC: int
SQL_ATTR_EXTENDED_INDICATORS: int
SQL_ATTR_FET_BUF_SIZE: int
SQL_ATTR_FORCE_ROLLBACK: int
SQL_ATTR_FREE_LOCATORS_ON_FETCH: int
SQL_ATTR_GET_LATEST_MEMBER: int
SQL_ATTR_GET_LATEST_MEMBER_NAME: int
SQL_ATTR_IGNORE_SERVER_LIST: int
SQL_ATTR_INFO_CRRTKN: int
SQL_ATTR_INFO_PROGRAMID: int
SQL_ATTR_KEEP_DYNAMIC: int
SQL_ATTR_LOB_CACHE_SIZE: int
SQL_ATTR_LOB_FILE_THRESHOLD: int
SQL_ATTR_LOGIN_TIMEOUT: int
SQL_ATTR_LONGDATA_COMPAT: int
SQL_ATTR_MAPCHAR: int
SQL_ATTR_MAXBLKEXT: int
SQL_ATTR_MAX_LOB_BLOCK_SIZE: int
SQL_ATTR_NETWORK_STATISTICS: int
SQL_ATTR_OVERRIDE_CHARACTER_CODEPAGE: int
SQL_ATTR_OVERRIDE_CODEPAGE: int
SQL_ATTR_OVERRIDE_PRIMARY_AFFINITY: int
SQL_ATTR_PARC_BATCH: int
SQL_ATTR_PING_DB: int
SQL_ATTR_PING_NTIMES: int
SQL_ATTR_PING_REQUEST_PACKET_SIZE: int
SQL_ATTR_QUERY_PREFETCH: int
SQL_ATTR_QUIET_MODE: int
SQL_ATTR_READ_ONLY_CONNECTION: int
SQL_ATTR_RECEIVE_TIMEOUT: int
SQL_ATTR_REOPT: int
SQL_ATTR_REPORT_ISLONG_FOR_LONGTYPES_OLEDB: int
SQL_ATTR_REPORT_SEAMLESSFAILOVER_WARNING: int
SQL_ATTR_REPORT_TIMESTAMP_TRUNC_AS_WARN: int
SQL_ATTR_RETRYONERROR: int
SQL_ATTR_RETRY_ON_MERGE: int
SQL_ATTR_SERVER_MSGTXT_MASK: int
SQL_ATTR_SERVER_MSGTXT_SP: int
SQL_ATTR_SESSION_GLOBAL_VAR: int
SQL_ATTR_SESSION_TIME_ZONE: int
SQL_ATTR_SPECIAL_REGISTER: int
SQL_ATTR_SQLCOLUMNS_SORT_BY_ORDINAL_OLEDB: int
SQL_ATTR_STMT_CONCENTRATOR: int
SQL_ATTR_STREAM_GETDATA: int
SQL_ATTR_STREAM_OUTPUTLOB_ON_CALL: int
SQL_ATTR_TIME_FMT: int
SQL_ATTR_TIME_SEP: int
SQL_ATTR_TRUSTED_CONTEXT_ACCESSTOKEN: int
SQL_ATTR_USER_REGISTRY_NAME: int
SQL_ATTR_WCHARTYPE: int

@final
class IBM_DBClientInfo:
    def __new__(cls, *args: object, **kwargs: object) -> Self: ...
    APPL_CODEPAGE: int
    CONN_CODEPAGE: int
    DATA_SOURCE_NAME: str
    DRIVER_NAME: str
    DRIVER_ODBC_VER: str
    DRIVER_VER: str
    ODBC_SQL_CONFORMANCE: str
    ODBC_VER: str

@final
class IBM_DBConnection:
    def __new__(cls, *args: object, **kwargs: object) -> Self: ...

@final
class IBM_DBServerInfo:
    def __new__(cls, *args: object, **kwargs: object) -> Self: ...
    DBMS_NAME: str
    DBMS_VER: str
    DB_CODEPAGE: int
    DB_NAME: str
    DFT_ISOLATION: str
    IDENTIFIER_QUOTE_CHAR: str
    INST_NAME: str
    ISOLATION_OPTION: tuple[str, str, str, str, str]
    KEYWORDS: str
    LIKE_ESCAPE_CLAUSE: bool
    MAX_COL_NAME_LEN: int
    MAX_IDENTIFIER_LEN: int
    MAX_INDEX_SIZE: int
    MAX_PROC_NAME_LEN: int
    MAX_ROW_SIZE: int
    MAX_SCHEMA_NAME_LEN: int
    MAX_STATEMENT_LEN: int
    MAX_TABLE_NAME_LEN: int
    NON_NULLABLE_COLUMNS: bool
    PROCEDURES: bool
    SPECIAL_CHARS: str
    SQL_CONFORMANCE: str

@final
class IBM_DBStatement:
    def __new__(cls, *args: object, **kwargs: object) -> Self: ...

def active(__connection: IBM_DBConnection | None) -> bool: ...
def autocommit(__connection: IBM_DBConnection, __value: int = ...) -> int | bool: ...
def bind_param(
    __stmt: IBM_DBStatement,
    __parameter_number: int,
    __variable: str,
    __parameter_type: int | None = ...,
    __data_type: int | None = ...,
    __precision: int | None = ...,
    __scale: int | None = ...,
    __size: int | None = ...,
) -> bool: ...
@overload
def callproc(__connection: IBM_DBConnection, __procname: str) -> IBM_DBStatement | None: ...
@overload
def callproc(__connection: IBM_DBConnection, __procname: str, __parameters: tuple[object, ...]) -> tuple[object, ...] | None: ...
def check_function_support(__connection: IBM_DBConnection, __function_id: int) -> bool: ...
def client_info(__connection: IBM_DBConnection) -> IBM_DBClientInfo | bool: ...
def close(__connection: IBM_DBConnection) -> bool: ...
def column_privileges(
    __connection: IBM_DBConnection,
    __qualifier: str | None = ...,
    __schema: str | None = ...,
    __table_name: str | None = ...,
    __column_name: str | None = ...,
) -> IBM_DBStatement: ...
def columns(
    __connection: IBM_DBConnection,
    __qualifier: str | None = ...,
    __schema: str | None = ...,
    __table_name: str | None = ...,
    __column_name: str | None = ...,
) -> IBM_DBStatement: ...
def commit(__connection: IBM_DBConnection) -> bool: ...
def conn_error(__connection: IBM_DBConnection | None = ...) -> str: ...
def conn_errormsg(__connection: IBM_DBConnection | None = ...) -> str: ...
def conn_warn(__connection: IBM_DBConnection | None = ...) -> str: ...
def connect(
    __database: str,
    __user: str,
    __password: str,
    __options: dict[int, int | str] | None = ...,
    __replace_quoted_literal: int = ...,
) -> IBM_DBConnection | None: ...
def createdb(__connection: IBM_DBConnection, __dbName: str, __codeSet: str = ..., __mode: str = ...) -> bool: ...
def createdbNX(__connection: IBM_DBConnection, __dbName: str, __codeSet: str = ..., __mode: str = ...) -> bool: ...
def cursor_type(__stmt: IBM_DBStatement) -> int: ...
def dropdb(__connection: IBM_DBConnection, __dbName: str) -> bool: ...
def exec_immediate(
    __connection: IBM_DBConnection, __statement: str | None, __options: dict[int, int] = ...
) -> IBM_DBStatement | bool: ...
def execute(__stmt: IBM_DBStatement, __parameters: tuple[object, ...] | None = ...) -> bool: ...
def execute_many(
    __stmt: IBM_DBStatement, __seq_of_parameters: tuple[object, ...], __options: dict[int, int] = ...
) -> int | None: ...
def fetch_assoc(__stmt: IBM_DBStatement, __row_number: int = ...) -> dict[str, object] | bool: ...
def fetch_both(__stmt: IBM_DBStatement, __row_number: int = ...) -> dict[int | str, object] | bool: ...
def fetch_row(__stmt: IBM_DBStatement, __row_number: int = ...) -> bool: ...
def fetch_tuple(__stmt: IBM_DBStatement, __row_number: int = ...) -> tuple[object, ...]: ...
def field_display_size(__stmt: IBM_DBStatement, __column: int | str) -> int | bool: ...
def field_name(__stmt: IBM_DBStatement, __column: int | str) -> str | bool: ...
def field_nullable(__stmt: IBM_DBStatement, __column: int | str) -> bool: ...
def field_num(__stmt: IBM_DBStatement, __column: int | str) -> int | bool: ...
def field_precision(__stmt: IBM_DBStatement, __column: int | str) -> int | bool: ...
def field_scale(__stmt: IBM_DBStatement, __column: int | str) -> int | bool: ...
def field_type(__stmt: IBM_DBStatement, __column: int | str) -> str | bool: ...
def field_width(__stmt: IBM_DBStatement, __column: int | str) -> int | bool: ...
def foreign_keys(
    __connection: IBM_DBConnection,
    __pk_qualifier: str | None,
    __pk_schema: str | None,
    __pk_table_name: str | None,
    __fk_qualifier: str | None = ...,
    __fk_schema: str | None = ...,
    __fk_table_name: str | None = ...,
) -> IBM_DBStatement: ...
def free_result(__stmt: IBM_DBStatement) -> bool: ...
def free_stmt(__stmt: IBM_DBStatement) -> bool: ...
def get_db_info(__connection: IBM_DBConnection, __option: int) -> str | bool: ...
def get_last_serial_value(__stmt: IBM_DBStatement) -> str | bool: ...
def get_num_result(__stmt: IBM_DBStatement) -> int | bool: ...
def get_option(__resc: IBM_DBConnection | IBM_DBStatement, __options: int, __type: int) -> Any: ...
def next_result(__stmt: IBM_DBStatement) -> IBM_DBStatement | bool: ...
def num_fields(__stmt: IBM_DBStatement) -> int | bool: ...
def num_rows(__stmt: IBM_DBStatement) -> int: ...
def pconnect(
    __database: str, __username: str, __password: str, __options: dict[int, int | str] | None = ...
) -> IBM_DBConnection | None: ...
def prepare(
    __connection: IBM_DBConnection, __statement: str, __options: dict[int, int | str] | None = ...
) -> IBM_DBStatement | bool: ...
def primary_keys(
    __connection: IBM_DBConnection, __qualifier: str | None, __schema: str | None, __table_name: str | None
) -> IBM_DBStatement: ...
def procedure_columns(
    __connection: IBM_DBConnection,
    __qualifier: str | None,
    __schema: str | None,
    __procedure: str | None,
    __parameter: str | None,
) -> IBM_DBStatement | bool: ...
def procedures(
    __connection: IBM_DBConnection, __qualifier: str | None, __schema: str | None, __procedure: str | None
) -> IBM_DBStatement | bool: ...
def recreatedb(__connection: IBM_DBConnection, __dbName: str, __codeSet: str | None = ..., __mode: str | None = ...) -> bool: ...
def result(__stmt: IBM_DBStatement, __column: int | str) -> Any: ...
def rollback(__connection: IBM_DBConnection) -> bool: ...
def server_info(__connection: IBM_DBConnection) -> IBM_DBServerInfo | bool: ...
def set_option(__resc: IBM_DBConnection | IBM_DBStatement, __options: dict[int, int | str], __type: int) -> bool: ...
def special_columns(
    __connection: IBM_DBConnection, __qualifier: str | None, __schema: str | None, __table_name: str | None, __scope: int
) -> IBM_DBStatement: ...
def statistics(
    __connection: IBM_DBConnection, __qualifier: str | None, __schema: str | None, __table_name: str | None, __unique: bool | None
) -> IBM_DBStatement: ...
def stmt_error(__stmt: IBM_DBStatement = ...) -> str: ...
def stmt_errormsg(__stmt: IBM_DBStatement = ...) -> str: ...
def stmt_warn(__connection: IBM_DBConnection = ...) -> IBM_DBStatement: ...
def table_privileges(
    __connection: IBM_DBConnection, __qualifier: str | None = ..., __schema: str | None = ..., __table_name: str | None = ...
) -> IBM_DBStatement | bool: ...
def tables(
    __connection: IBM_DBConnection,
    __qualifier: str | None = ...,
    __schema: str | None = ...,
    __table_name: str | None = ...,
    __table_type: str | None = ...,
) -> IBM_DBStatement | bool: ...
