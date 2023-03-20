from typing import Any

class ExtendedOperationContainer:
    def __init__(self, connection) -> None: ...

class StandardExtendedOperations(ExtendedOperationContainer):
    def who_am_i(self, controls: Any | None = ...): ...
    def modify_password(
        self,
        user: Any | None = ...,
        old_password: Any | None = ...,
        new_password: Any | None = ...,
        hash_algorithm: Any | None = ...,
        salt: Any | None = ...,
        controls: Any | None = ...,
    ): ...
    def paged_search(
        self,
        search_base,
        search_filter,
        search_scope=...,
        dereference_aliases=...,
        attributes: Any | None = ...,
        size_limit: int = ...,
        time_limit: int = ...,
        types_only: bool = ...,
        get_operational_attributes: bool = ...,
        controls: Any | None = ...,
        paged_size: int = ...,
        paged_criticality: bool = ...,
        generator: bool = ...,
    ): ...
    def persistent_search(
        self,
        search_base: str = ...,
        search_filter: str = ...,
        search_scope=...,
        dereference_aliases=...,
        attributes=...,
        size_limit: int = ...,
        time_limit: int = ...,
        controls: Any | None = ...,
        changes_only: bool = ...,
        show_additions: bool = ...,
        show_deletions: bool = ...,
        show_modifications: bool = ...,
        show_dn_modifications: bool = ...,
        notifications: bool = ...,
        streaming: bool = ...,
        callback: Any | None = ...,
    ): ...
    def funnel_search(
        self,
        search_base: str = ...,
        search_filter: str = ...,
        search_scope=...,
        dereference_aliases=...,
        attributes=...,
        size_limit: int = ...,
        time_limit: int = ...,
        controls: Any | None = ...,
        streaming: bool = ...,
        callback: Any | None = ...,
    ): ...

class NovellExtendedOperations(ExtendedOperationContainer):
    def get_bind_dn(self, controls: Any | None = ...): ...
    def get_universal_password(self, user, controls: Any | None = ...): ...
    def set_universal_password(self, user, new_password: Any | None = ..., controls: Any | None = ...): ...
    def list_replicas(self, server_dn, controls: Any | None = ...): ...
    def partition_entry_count(self, partition_dn, controls: Any | None = ...): ...
    def replica_info(self, server_dn, partition_dn, controls: Any | None = ...): ...
    def start_transaction(self, controls: Any | None = ...): ...
    def end_transaction(self, commit: bool = ..., controls: Any | None = ...): ...
    def add_members_to_groups(self, members, groups, fix: bool = ..., transaction: bool = ...): ...
    def remove_members_from_groups(self, members, groups, fix: bool = ..., transaction: bool = ...): ...
    def check_groups_memberships(self, members, groups, fix: bool = ..., transaction: bool = ...): ...

class MicrosoftExtendedOperations(ExtendedOperationContainer):
    def dir_sync(
        self,
        sync_base,
        sync_filter: str = ...,
        attributes=...,
        cookie: Any | None = ...,
        object_security: bool = ...,
        ancestors_first: bool = ...,
        public_data_only: bool = ...,
        incremental_values: bool = ...,
        max_length: int = ...,
        hex_guid: bool = ...,
    ): ...
    def modify_password(self, user, new_password, old_password: Any | None = ..., controls: Any | None = ...): ...
    def unlock_account(self, user): ...
    def add_members_to_groups(self, members, groups, fix: bool = ...): ...
    def remove_members_from_groups(self, members, groups, fix: bool = ...): ...
    def persistent_search(
        self, search_base: str = ..., search_scope=..., attributes=..., streaming: bool = ..., callback: Any | None = ...
    ): ...

class ExtendedOperationsRoot(ExtendedOperationContainer):
    standard: Any
    novell: Any
    microsoft: Any
    def __init__(self, connection) -> None: ...
