from test_helper import run_common_tests, failed, passed, import_task_file, get_task_windows


def test_task_window():
    window = get_task_windows()[0]
    if "another value" == window:
      failed("You should redefine variable 'greetings'")
    else:
      passed()

def test_value():
    file = import_task_file()

    if file.greetings == "greetings":
        failed("You should assign different value to the variable")
    else:
        passed()

if __name__ == '__main__':
    test_value()
    run_common_tests("You should redefine variable 'greetings'")
