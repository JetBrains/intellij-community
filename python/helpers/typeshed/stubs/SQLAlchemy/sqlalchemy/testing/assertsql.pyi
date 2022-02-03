from typing import Any

class AssertRule:
    is_consumed: bool
    errormessage: Any
    consume_statement: bool
    def process_statement(self, execute_observed) -> None: ...
    def no_more_statements(self) -> None: ...

class SQLMatchRule(AssertRule): ...

class CursorSQL(SQLMatchRule):
    statement: Any
    params: Any
    consume_statement: Any
    def __init__(self, statement, params: Any | None = ..., consume_statement: bool = ...) -> None: ...
    errormessage: Any
    is_consumed: bool
    def process_statement(self, execute_observed) -> None: ...

class CompiledSQL(SQLMatchRule):
    statement: Any
    params: Any
    dialect: Any
    def __init__(self, statement, params: Any | None = ..., dialect: str = ...) -> None: ...
    is_consumed: bool
    errormessage: Any
    def process_statement(self, execute_observed) -> None: ...

class RegexSQL(CompiledSQL):
    regex: Any
    orig_regex: Any
    params: Any
    dialect: Any
    def __init__(self, regex, params: Any | None = ..., dialect: str = ...) -> None: ...

class DialectSQL(CompiledSQL): ...

class CountStatements(AssertRule):
    count: Any
    def __init__(self, count) -> None: ...
    def process_statement(self, execute_observed) -> None: ...
    def no_more_statements(self) -> None: ...

class AllOf(AssertRule):
    rules: Any
    def __init__(self, *rules) -> None: ...
    is_consumed: bool
    errormessage: Any
    def process_statement(self, execute_observed) -> None: ...

class EachOf(AssertRule):
    rules: Any
    def __init__(self, *rules) -> None: ...
    errormessage: Any
    is_consumed: bool
    def process_statement(self, execute_observed) -> None: ...
    def no_more_statements(self) -> None: ...

class Conditional(EachOf):
    def __init__(self, condition, rules, else_rules) -> None: ...

class Or(AllOf):
    is_consumed: bool
    errormessage: Any
    def process_statement(self, execute_observed) -> None: ...

class SQLExecuteObserved:
    context: Any
    clauseelement: Any
    parameters: Any
    statements: Any
    def __init__(self, context, clauseelement, multiparams, params) -> None: ...

class SQLCursorExecuteObserved: ...

class SQLAsserter:
    accumulated: Any
    def __init__(self) -> None: ...
    def assert_(self, *rules) -> None: ...

def assert_engine(engine) -> None: ...
