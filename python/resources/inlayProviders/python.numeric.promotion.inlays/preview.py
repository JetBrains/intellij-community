# Numeric type promotion hints show implicit type widening
def calculate(
    value: float/*<#  | int#>*/,
    result: complex/*<#  | float | int#>*/,
):
    pass


# No hints when union already includes promoted types
def explicit_unions(
    a: float | int,
    b: complex | float | int,
):
    pass
