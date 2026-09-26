from typing import dataclass_transform


class FieldKwOnlyDefaultFalse:
    def __init__(self, kw_only: bool = False):
        ...


class FieldKwOnlyDefaultTrue:
    def __init__(self, kw_only: bool = True):
        ...


class FieldNoOwnParams:
    def __init__(self):
        ...


@dataclass_transform(field_specifiers=(FieldKwOnlyDefaultFalse, FieldKwOnlyDefaultTrue, FieldNoOwnParams))
class MyDataclass:
    def __init_subclass__(cls, **kwargs):
        ...


@dataclass_transform(kw_only_default=True,
                     field_specifiers=(FieldKwOnlyDefaultFalse, FieldKwOnlyDefaultTrue, FieldNoOwnParams))
class MyDataclassKwOnlyDefaultTrue:
    def __init_subclass__(cls, **kwargs):
        ...


@dataclass_transform(kw_only_default=False,
                     field_specifiers=(FieldKwOnlyDefaultFalse, FieldKwOnlyDefaultTrue, FieldNoOwnParams))
class MyDataclassKwOnlyDefaultFalse:
    def __init_subclass__(cls, **kwargs):
        ...


class DataclassKwOnlyTrue(MyDataclass, kw_only=True):
    not_kw_only_spec_default: int = FieldKwOnlyDefaultFalse()
    not_kw_only_spec_arg: int = FieldKwOnlyDefaultTrue(kw_only=False)
    kw_only_inferred: int = FieldNoOwnParams()
    kw_only_spec_default: int = FieldKwOnlyDefaultTrue()
    kw_only_spec_arg: int = FieldKwOnlyDefaultFalse(kw_only=True)


class DataclassKwOnlyDefaultTrue(MyDataclassKwOnlyDefaultTrue):
    not_kw_only_spec_default: int = FieldKwOnlyDefaultFalse()
    not_kw_only_spec_arg: int = FieldKwOnlyDefaultTrue(kw_only=False)
    kw_only_inferred: int = FieldNoOwnParams()
    kw_only_spec_default: int = FieldKwOnlyDefaultTrue()
    kw_only_spec_arg: int = FieldKwOnlyDefaultFalse(kw_only=True)


class DataclassKwOnlyFalse(MyDataclass, kw_only=False):
    not_kw_only_spec_default: int = FieldKwOnlyDefaultFalse()
    not_kw_only_spec_arg: int = FieldKwOnlyDefaultTrue(kw_only=False)
    not_kw_only_inferred: int = FieldNoOwnParams()
    kw_only_spec_default: int = FieldKwOnlyDefaultTrue()
    kw_only_spec_arg: int = FieldKwOnlyDefaultFalse(kw_only=True)


class DataclassKwOnlyDefaultFalse(MyDataclassKwOnlyDefaultFalse):
    not_kw_only_spec_default: int = FieldKwOnlyDefaultFalse()
    not_kw_only_spec_arg: int = FieldKwOnlyDefaultTrue(kw_only=False)
    not_kw_only_inferred: int = FieldNoOwnParams()
    kw_only_spec_default: int = FieldKwOnlyDefaultTrue()
    kw_only_spec_arg: int = FieldKwOnlyDefaultFalse(kw_only=True)


class DataclassImplicitKwOnlyFalse(MyDataclass):
    not_kw_only_spec_default: int = FieldKwOnlyDefaultFalse()
    not_kw_only_spec_arg: int = FieldKwOnlyDefaultTrue(kw_only=False)
    not_kw_only_inferred: int = FieldNoOwnParams()
    kw_only_spec_default: int = FieldKwOnlyDefaultTrue()
    kw_only_spec_arg: int = FieldKwOnlyDefaultFalse(kw_only=True)


DataclassKwOnlyTrue(<arg1>)
DataclassKwOnlyDefaultTrue(<arg2>)
DataclassKwOnlyFalse(<arg3>)
DataclassKwOnlyDefaultFalse(<arg4>)
DataclassImplicitKwOnlyFalse(<arg5>)
