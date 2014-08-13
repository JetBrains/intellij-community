from test_helper import run_common_tests, passed, failed, import_task_file, get_task_windows


def test_value():
    file = import_task_file()
    if file.is_greater:
        passed()
    else:
        failed("Use > operator")


def test_window():
    window = get_task_windows()[0]
    if ">" in window:
        passed()
    else:
        failed("Use > operator")

if __name__ == '__main__':
    run_common_tests("You should modify the file")
    test_value()
    test_window()