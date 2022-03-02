from typing import Any

from ..sql.base import CompileState, Options
from ..sql.dml import DeleteDMLState, UpdateDMLState

def save_obj(base_mapper, states, uowtransaction, single: bool = ...) -> None: ...
def post_update(base_mapper, states, uowtransaction, post_update_cols) -> None: ...
def delete_obj(base_mapper, states, uowtransaction) -> None: ...

class BulkUDCompileState(CompileState):
    class default_update_options(Options): ...

    @classmethod
    def orm_pre_session_exec(cls, session, statement, params, execution_options, bind_arguments, is_reentrant_invoke): ...
    @classmethod
    def orm_setup_cursor_result(cls, session, statement, params, execution_options, bind_arguments, result): ...

class BulkORMUpdate(UpdateDMLState, BulkUDCompileState):
    mapper: Any
    extra_criteria_entities: Any
    @classmethod
    def create_for_statement(cls, statement, compiler, **kw): ...

class BulkORMDelete(DeleteDMLState, BulkUDCompileState):
    mapper: Any
    extra_criteria_entities: Any
    @classmethod
    def create_for_statement(cls, statement, compiler, **kw): ...
