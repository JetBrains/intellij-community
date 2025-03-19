from dataclasses import dataclass


@dataclass
class Foo:
    param0: str
    param1: str
    param2: str
    param3: str
    param4: str
    param5: str
    param6: str
    param7: str
    param8: str
    param9: str
    param10: str
    param11: str
    param12: str
    param13: str


foo = Foo(
    param0="param0 long value",
    param1="param1 long value",
    param2="param2 long value",
    param3="param3 long value",
    param4="param4 long value",
    param5="param5 long value",
    param6="param6 long value",
    param7="param7 long value",
    param8="param8 long value",
    param9="param9 long value",
    param10="param10 long value",
    param11="param11 long value",
    param12="param12 long value",
    param13="param13 long value",
)

print(foo)  # breakpoint