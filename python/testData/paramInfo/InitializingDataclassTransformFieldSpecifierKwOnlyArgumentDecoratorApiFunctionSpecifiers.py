from typing import Callable, dataclass_transform

def field_kw_only_default_false(kw_only: bool = False):
    ...

def field_kw_only_default_true(kw_only: bool = True):
    ...

def field_no_own_params():
    ...


@dataclass_transform(field_specifiers=(field_kw_only_default_false, field_kw_only_default_true, field_no_own_params))
def my_dataclass(**kwargs) -> Callable[[type], type]:
    ...

@dataclass_transform(kw_only_default=True, field_specifiers=(field_kw_only_default_false, field_kw_only_default_true, field_no_own_params))
def my_dataclass_kw_only_default_true(**kwargs) -> Callable[[type], type]:
    ...


@dataclass_transform(kw_only_default=False, field_specifiers=(field_kw_only_default_false, field_kw_only_default_true, field_no_own_params))
def my_dataclass_kw_only_default_false(**kwargs) -> Callable[[type], type]:
    ...


@my_dataclass(kw_only=True)
class DataclassKwOnlyTrue:
    not_kw_only_spec_default: int = field_kw_only_default_false()
    not_kw_only_spec_arg: int = field_kw_only_default_true(kw_only=False)
    kw_only_inferred: int = field_no_own_params()
    kw_only_spec_default: int = field_kw_only_default_true()
    kw_only_spec_arg: int = field_kw_only_default_false(kw_only=True)


@my_dataclass_kw_only_default_true()
class DataclassKwOnlyDefaultTrue:
    not_kw_only_spec_default: int = field_kw_only_default_false()
    not_kw_only_spec_arg: int = field_kw_only_default_true(kw_only=False)
    kw_only_inferred: int = field_no_own_params()
    kw_only_spec_default: int = field_kw_only_default_true()
    kw_only_spec_arg: int = field_kw_only_default_false(kw_only=True)
    

@my_dataclass(kw_only=False)
class DataclassKwOnlyFalse:
    not_kw_only_spec_default: int = field_kw_only_default_false()
    not_kw_only_spec_arg: int = field_kw_only_default_true(kw_only=False)
    not_kw_only_inferred: int = field_no_own_params()
    kw_only_spec_default: int = field_kw_only_default_true()
    kw_only_spec_arg: int = field_kw_only_default_false(kw_only=True)


@my_dataclass_kw_only_default_false()
class DataclassKwOnlyDefaultFalse:
    not_kw_only_spec_default: int = field_kw_only_default_false()
    not_kw_only_spec_arg: int = field_kw_only_default_true(kw_only=False)
    not_kw_only_inferred: int = field_no_own_params()
    kw_only_spec_default: int = field_kw_only_default_true()
    kw_only_spec_arg: int = field_kw_only_default_false(kw_only=True)


@my_dataclass()
class DataclassImplicitKwOnlyFalse:
    not_kw_only_spec_default: int = field_kw_only_default_false()
    not_kw_only_spec_arg: int = field_kw_only_default_true(kw_only=False)
    not_kw_only_inferred: int = field_no_own_params()
    kw_only_spec_default: int = field_kw_only_default_true()
    kw_only_spec_arg: int = field_kw_only_default_false(kw_only=True)


DataclassKwOnlyTrue(<arg1>)
DataclassKwOnlyDefaultTrue(<arg2>)
DataclassKwOnlyFalse(<arg3>)
DataclassKwOnlyDefaultFalse(<arg4>)
DataclassImplicitKwOnlyFalse(<arg5>)