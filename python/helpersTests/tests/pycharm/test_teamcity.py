import pytest

pytest_plugins = "pytester"


# PY-84850
def test_teamcity_plugin_with_xdist(pytester):
    pytester.makepyfile("""
        def test_example_1():
            assert 1 + 1 == 2

        def test_example_2():
            assert 2 * 2 == 4

        def test_example_3():
            assert 3 - 1 == 2
    """)

    result = pytester.runpytest("-p", "teamcity.pytest_plugin", "-n", "2", "--teamcity")

    assert result.ret == 0
    output = result.stdout.str()
    assert "##teamcity[testStarted" in output
    assert "##teamcity[testFinished" in output


# PY-84850
def test_teamcity_plugin_without_xdist(pytester):
    pytester.makepyfile("""
        def test_basic():
            assert True
    """)

    result = pytester.runpytest("-p", "teamcity.pytest_plugin", "--teamcity")

    assert result.ret == 0
    output = result.stdout.str()
    assert "##teamcity[testStarted" in output
    assert "##teamcity[testFinished" in output
