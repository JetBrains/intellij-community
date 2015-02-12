import sys


def get_file_text(path):
    """ Returns file text by path"""
    file_io = open(path, "r")
    text = file_io.read()
    file_io.close()
    return text


def get_file_output(encoding="utf-8", path=sys.argv[-1]):
    """
    Returns answer file output
    :param encoding: to decode output in python3
    :param path: path of file to execute
    :return: list of strings
    """
    import subprocess

    proc = subprocess.Popen([sys.executable, path], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    return list(map(lambda x: str(x.decode(encoding)), proc.communicate()[0].splitlines()))


def test_file_importable():
    """ Tests there is no obvious syntax errors"""
    path = sys.argv[-1]
    if not path.endswith(".py"):
        import os

        parent = os.path.abspath(os.path.join(path, os.pardir))
        python_files = [f for f in os.listdir(parent) if os.path.isfile(os.path.join(parent, f)) and f.endswith(".py")]
        for python_file in python_files:
            if python_file == "tests.py": continue
            check_importable_path(os.path.join(parent, python_file))
        return
    check_importable_path(path)


def check_importable_path(path):
    """ Checks that file is importable.
        Reports failure otherwise.
    """
    try:
        import_file(path)
    except:
        failed("The file contains syntax errors", test_file_importable.__name__)
        return
    passed(test_file_importable.__name__)


def import_file(path):
    """ Returns imported file """
    if sys.version_info[0] == 2 or sys.version_info[1] < 3:
        import imp

        return imp.load_source("tmp", path)
    elif sys.version_info[0] == 3:
        import importlib.machinery

        return importlib.machinery.SourceFileLoader("tmp", path).load_module("tmp")


def import_task_file():
    """ Returns imported file.
        Imports file from which check action was run
    """
    path = sys.argv[-1]
    return import_file(path)


def test_is_not_empty():
    """
        Checks that file is not empty
    """
    path = sys.argv[-1]
    file_text = get_file_text(path)

    if len(file_text) > 0:
        passed()
    else:
        failed("The file is empty. Please, reload the task and try again.")


def test_is_initial_text(error_text="You should modify the file"):
    """
        Checks that file was modified
    """
    path = sys.argv[-1]
    text = get_initial_text(path)
    file_text = get_file_text(path)

    if file_text.strip() == text:
        failed(error_text)
    else:
        passed()


def get_initial_text(path):
    """
        Returns the initial task text
    """
    course_lib = sys.argv[-2]

    import os
    # path format is "project_root/lessonX/taskY/file.py"
    task_index = path.rfind(os.sep, 0, path.rfind(os.sep))
    index = path.rfind(os.sep, 0, task_index)
    relative_path = path[index + 1:]
    initial_file_path = os.path.join(course_lib, relative_path)
    return get_file_text(initial_file_path)


def test_text_equals(text, error_text):
    """
        Checks that answer equals text.
    """
    path = sys.argv[-1]
    file_text = get_file_text(path)

    if file_text.strip() == text:
        passed()
    else:
        failed(error_text)


def test_answer_placeholders_text_deleted(error_text="Don't just delete task text"):
    """
        Checks that all answer placeholders are not empty
    """
    windows = get_answer_placeholders()

    for window in windows:
        if len(window) == 0:
            failed(error_text)
            return
    passed()


def failed(message="Please, reload the task and try again.", name=None):
    """ Reports failure """
    if not name:
        name = sys._getframe().f_back.f_code.co_name
    print("#educational_plugin " + name + " FAILED + " + message)


def passed(name=None):
    """ Reports success """
    if not name:
        name = sys._getframe().f_back.f_code.co_name
    print("#educational_plugin " + name + " test OK")


def get_answer_placeholders():
    """
        Returns all answer placeholders text
    """
    prefix = "#educational_plugin_window = "
    path = sys.argv[-1]
    import os

    file_name_without_extension = os.path.splitext(path)[0]
    windows_path = file_name_without_extension + "_windows"
    windows = []
    f = open(windows_path, "r")
    window_text = ""
    first = True
    for line in f.readlines():
        if line.startswith(prefix):
            if not first:
                windows.append(window_text.strip())
            else:
                first = False
            window_text = line[len(prefix):]
        else:
            window_text += line

    if window_text:
        windows.append(window_text.strip())

    f.close()
    return windows


def run_common_tests(error_text="Please, reload file and try again"):
    test_is_initial_text()
    test_is_not_empty()
    test_answer_placeholders_text_deleted()
    test_file_importable()
