from typing import Any

class Message:
    id: Any
    string: Any
    locations: Any
    flags: Any
    auto_comments: Any
    user_comments: Any
    previous_id: Any
    lineno: Any
    context: Any
    def __init__(
        self,
        id,
        string: str = ...,
        locations=...,
        flags=...,
        auto_comments=...,
        user_comments=...,
        previous_id=...,
        lineno: Any | None = ...,
        context: Any | None = ...,
    ) -> None: ...
    def __cmp__(self, other): ...
    def __gt__(self, other): ...
    def __lt__(self, other): ...
    def __ge__(self, other): ...
    def __le__(self, other): ...
    def __eq__(self, other): ...
    def __ne__(self, other): ...
    def clone(self): ...
    def check(self, catalog: Any | None = ...): ...
    @property
    def fuzzy(self): ...
    @property
    def pluralizable(self): ...
    @property
    def python_format(self): ...

class TranslationError(Exception): ...

class Catalog:
    domain: Any
    locale: Any
    project: Any
    version: Any
    copyright_holder: Any
    msgid_bugs_address: Any
    last_translator: Any
    language_team: Any
    charset: Any
    creation_date: Any
    revision_date: Any
    fuzzy: Any
    obsolete: Any
    def __init__(
        self,
        locale: Any | None = ...,
        domain: Any | None = ...,
        header_comment=...,
        project: Any | None = ...,
        version: Any | None = ...,
        copyright_holder: Any | None = ...,
        msgid_bugs_address: Any | None = ...,
        creation_date: Any | None = ...,
        revision_date: Any | None = ...,
        last_translator: Any | None = ...,
        language_team: Any | None = ...,
        charset: Any | None = ...,
        fuzzy: bool = ...,
    ) -> None: ...
    @property
    def locale_identifier(self): ...
    header_comment: Any
    mime_headers: Any
    @property
    def num_plurals(self): ...
    @property
    def plural_expr(self): ...
    @property
    def plural_forms(self): ...
    def __contains__(self, id): ...
    def __len__(self): ...
    def __iter__(self): ...
    def __delitem__(self, id) -> None: ...
    def __getitem__(self, id): ...
    def __setitem__(self, id, message) -> None: ...
    def add(
        self,
        id,
        string: Any | None = ...,
        locations=...,
        flags=...,
        auto_comments=...,
        user_comments=...,
        previous_id=...,
        lineno: Any | None = ...,
        context: Any | None = ...,
    ): ...
    def check(self) -> None: ...
    def get(self, id, context: Any | None = ...): ...
    def delete(self, id, context: Any | None = ...) -> None: ...
    def update(
        self, template, no_fuzzy_matching: bool = ..., update_header_comment: bool = ..., keep_user_comments: bool = ...
    ) -> None: ...
