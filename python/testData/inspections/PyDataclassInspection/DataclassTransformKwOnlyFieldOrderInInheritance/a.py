from decorator import my_dataclass, my_dataclass_kw_only_default, my_field, my_field_kw_only_default, MyField, \
    MyFieldKwOnlyDefault


@my_dataclass(kw_only=True)
class KwOnlyExplicitClassParam:
    kw_only_default: int = 42
    kw_only_no_default: int


@my_dataclass()
class C1(KwOnlyExplicitClassParam):
    not_kw_only_no_default: int


@my_dataclass_kw_only_default()
class KwOnlyImplicitClassParam:
    kw_only_default: int = 42
    kw_only_no_default: int


@my_dataclass()
class C2(KwOnlyImplicitClassParam):
    not_kw_only_no_default: int


@my_dataclass()
class KwOnlyExplicitFieldParam:
    kw_only_default: int = my_field(default=42, kw_only=True)
    kw_only_default2: int = MyField(default=42, kw_only=True)
    kw_only_no_default: int


@my_dataclass()
class C3(KwOnlyExplicitFieldParam):
    not_kw_only_no_default: int


@my_dataclass()
class KwOnlyImplicitFieldParam:
    kw_only_default: int = my_field_kw_only_default(default=42)
    kw_only_default2: int = MyFieldKwOnlyDefault(default=42)
    kw_only_no_default: int


@my_dataclass()
class C4(KwOnlyImplicitFieldParam):
    not_kw_only_no_default: int


@my_dataclass()
class BaseNotKwOnlyDefault:
    not_kw_only_default: int = 42


@my_dataclass()
class <error descr="Non-default argument(s) follows default argument(s) defined in 'BaseNotKwOnlyDefault'">SubNotKwOnly</error>(BaseNotKwOnlyDefault):
    not_kw_only_no_default: int


@my_dataclass(kw_only=True)
class SubKwOnlyExplicitClassParam(BaseNotKwOnlyDefault):
    kw_only_no_default: int


@my_dataclass_kw_only_default()
class SubKwOnlyImplicitClassParam(BaseNotKwOnlyDefault):
    kw_only_no_default: int


@my_dataclass()
class SubKwOnlyExplicitFieldParam(BaseNotKwOnlyDefault):
    kw_only_no_default: int = my_field(kw_only=True)
    kw_only_no_default2: int = MyField(kw_only=True)


@my_dataclass()
class SubKwOnlyExplicitFieldParam(BaseNotKwOnlyDefault):
    kw_only_no_default: int = my_field_kw_only_default()
    kw_only_no_default2: int = MyFieldKwOnlyDefault()
