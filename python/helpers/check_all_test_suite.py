import sys
import re
import os.path

# @formatter:off
COMMUNITY_TEST_ROOT = os.path.expandvars('${DEV_IDEA_HOME}/community/python/testSrc')
ULTIMATE_TEST_ROOT = os.path.expandvars('${DEV_IDEA_HOME}/python/testSrc')
ALL_TEST_SUITE_CLASS = os.path.expandvars('${DEV_IDEA_HOME}/python/testSrc/com/jetbrains/python/PythonAllTestsSuite.java')
# @formatter:on

EXCLUDED_TESTS = {
    'FPTest',
    'IteratorsTest',
    'PyDuplocatorTest',
    'PyDjangoRightClickTest',  # not in "django" package
    'ChameleonTypingTest'
}

EXCLUDED_PACKAGES = {
    'env',
    'web2py',
    'django',
    'mako',
    'jinja2',
    'appengine',
    'buildout',
    'cython'
}


def check_test_suite(suite_class, *test_roots):
    def class_name(path):
        return os.path.splitext(os.path.basename(path))[0]

    def is_excluded(path):
        dir_path, file_name = os.path.split(path)
        if any(part in EXCLUDED_PACKAGES for part in dir_path.split(os.path.sep)):
            return True
        return class_name(file_name) in EXCLUDED_TESTS

    def is_abstract_class(path):
        with open(path, encoding='utf-8') as f:
            return bool(re.search(r'\babstract\b', f.read()))

    suite_test_names = set()
    with open(suite_class, encoding='utf-8') as f:
        for test_name in re.findall(r'(\w+(?:Test|TestCase))\.class', f.read()):
            if test_name in suite_test_names:
                print('Suite {} contains duplicate item {}'.format(class_name(suite_class),
                                                                   test_name),
                      file=sys.stderr)
            suite_test_names.add(test_name)

    missing_tests = []
    for test_root in test_roots:
        for dir_path, sub_dirs, files in os.walk(test_root):
            for pkg in EXCLUDED_PACKAGES:
                if pkg in sub_dirs:
                    sub_dirs.remove(pkg)

            for file_name in files:
                test_path = os.path.join(dir_path, file_name)
                test_name = class_name(file_name)
                if (test_name.endswith(('Test', 'TestCase')) and
                        not is_excluded(test_path) and
                        not is_abstract_class(test_path) and
                            test_name not in suite_test_names):
                    missing_tests.append(test_name)

    return missing_tests


if __name__ == '__main__':
    missing = check_test_suite(ALL_TEST_SUITE_CLASS, COMMUNITY_TEST_ROOT, ULTIMATE_TEST_ROOT)
    print(',\n'.join(name + '.class' for name in missing))
