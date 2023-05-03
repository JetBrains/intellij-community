import timeit
import pytest
import sys

pytest_plugins = [
    str('pydev_tests_python.debugger_fixtures'),
]

IS_PY2 = sys.version_info[0] == 2


def _test_case(case_setup, file_name, var_name, result_string):
    with case_setup.test_file('resources/reprlib_tests/' + file_name) as writer:
        line = writer.get_line_index_with_content('breakpoint')
        writer.write_add_breakpoint(line)
        writer.write_make_initial_run()

        hit = writer.wait_for_breakpoint_hit()
        writer.write_load_full_value(hit.thread_id, hit.frame_id, var_name)
        writer.wait_for_var(result_string)
        writer.write_run_thread(hit.thread_id)
        writer.finished_ok = True


def _test_performance(lambda_fun, test_threshold, repeat=2, number=20):
    time = timeit.repeat(lambda_fun,
                         repeat=repeat,
                         number=number)
    test_time = sum(time) / repeat / number
    # print("Average test time = ", test_time)
    assert test_time < test_threshold


@pytest.mark.skipif(IS_PY2, reason='Reprlib was added with Python 3')
def test_array(case_setup):
    _test_performance(
        lambda: _test_case(case_setup, '_array_case.py', 'array_var',
                           '<var name="a" type="array" qualifier="array"'),
        test_threshold=5)


@pytest.mark.skipif(IS_PY2, reason='Reprlib was added with Python 3')
def test_deque(case_setup):
    _test_performance(
        lambda: _test_case(case_setup, '_deque_case.py', 'deque_var',
                           '<var name="d" type="deque" qualifier="collections"'),
        test_threshold=5
    )


@pytest.mark.skipif(IS_PY2, reason='Reprlib was added with Python 3')
def test_dict(case_setup):
    _test_performance(
        lambda: _test_case(case_setup, '_dict_case.py', 'dict_var',
                           '<var name="d" type="dict" qualifier="builtins"'),
        test_threshold=5
    )


@pytest.mark.skipif(IS_PY2, reason='Reprlib was added with Python 3')
def test_list(case_setup):
    _test_performance(
        lambda: _test_case(case_setup, '_list_case.py', 'list_var',
                           '<var name="l" type="list" qualifier="builtins"'),
        test_threshold=5
    )


@pytest.mark.skipif(IS_PY2, reason='Reprlib was added with Python 3')
def test_set(case_setup):
    _test_performance(
        lambda: _test_case(case_setup, '_set_case.py', 'set_var',
                           '<var name="s" type="set" qualifier="builtins"'),
        test_threshold=5
    )


@pytest.mark.skipif(IS_PY2, reason='Reprlib was added with Python 3')
def test_frozenset(case_setup):
    _test_performance(
        lambda: _test_case(case_setup, '_frozenset_case.py', 'frozenset_var',
                           '<var name="f" type="frozenset" qualifier="builtins"'),
        test_threshold=5
    )


@pytest.mark.skipif(IS_PY2, reason='Reprlib was added with Python 3')
def test_tuple(case_setup):
    _test_performance(
        lambda: _test_case(case_setup, '_tuple_case.py', 'tuple_var',
                           '<var name="t" type="tuple" qualifier="builtins"'),
        test_threshold=5
    )


@pytest.mark.skipif(IS_PY2, reason='Reprlib was added with Python 3')
def test_user_dictionary(case_setup):
    _test_performance(
        lambda: _test_case(case_setup, '_user_dictionary_case.py', 'dict_var',
                           '<var name="d" type="dict" qualifier="builtins" value="'),
        test_threshold=5
    )


if __name__ == '__main__':
    pytest.main(['-k', 'test_unhandled_exceptions_in_top_level2'])
