from test_helper import run_common_tests, failed, passed, get_task_windows, import_task_file


def test_value():
    file = import_task_file()
    if file.length == 13:
        passed()
    else:
        failed("Count again")

def test_window():
    window = get_task_windows()[0]
    if "for " in window:
        passed()
    else:
        failed("Use for loop to iterate over 'hello_world' string")


if __name__ == '__main__':
    run_common_tests()
    test_window()
    test_value()