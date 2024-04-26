import pytest


@pytest.mark.parametrize(
    "one, two, three",
    [
        ([1, 2], [1, 2], 6),
        ([1, 2, 3], [1, 2, 3], 12),
        ([1, 2, 3, 4], [1, 2, 3, 4], 20),
    ],
)
def test_solution(o<caret>ne, two, three):
    assert sum(one) + sum(two) == three