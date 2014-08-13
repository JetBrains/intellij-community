from test_helper import run_common_tests, failed, passed, import_task_file, get_task_windows


def test_value():
    file = import_task_file()
    if file.python == "Python":
        passed()
    else:
        failed("Check indexes used in slicing")


def test_monty_python():
    window = get_task_windows()[0]
    if "monty_python" in window:
        passed()
    else:
        failed("Use slicing")


if __name__ == '__main__':
    run_common_tests()
    test_value()
    test_monty_python()