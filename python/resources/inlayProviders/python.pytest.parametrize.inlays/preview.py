import pytest

# Parametrize values with parameter names
@pytest.mark.parametrize("input_num, expected", [
    (/*<#input_num#>*/2, /*<#expected#>*/4),
    (/*<#input_num#>*/3, /*<#expected#>*/9),
    pytest.param(/*<#input_num#>*/4, /*<#expected#>*/16),
])
def test_square(input_num, expected):
    assert input_num ** 2 == expected
