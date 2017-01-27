# coding=utf-8
"""
Tools to implement runners (https://confluence.jetbrains.com/display/~link/PyCharm+test+runners+protocol)
"""
import argparse

import sys, os

import imp

from teamcity import teamcity_presence_env_var, messages

# Some runners need it to "detect" TC and start protocol
if teamcity_presence_env_var not in os.environ:
    os.environ[teamcity_presence_env_var] = "LOCAL"

# Monkeypatching TC to pass location hint
old_started = messages.TeamcityServiceMessages.message


def __jb_message(self, messageName, **properties):
    try:
        properties["locationHint"] = "python://{0}".format(properties["name"])
    except KeyError:
        pass
    print("\n")
    old_started(self, messageName, **properties)


messages.TeamcityServiceMessages.message = __jb_message


# Monkeypatched


def jb_patch_separator(targets, fs_glue, python_glue, fs_to_python_glue):
    """
    Targets are always dot separated according to manual.
    However, some runners may need different separators.
    This function splits target to file/symbol parts and glues them using provided glues.

    :param targets: list of dot-separated targets
    :param fs_glue: how to glue fs parts of target. I.e.: module "eggs" in "spam" package is "spam[fs_glue]eggs"
    :param python_glue: how to glue python parts (glue between class and function etc)
    :param fs_to_python_glue: between last fs-part and first python part
    :return: list of targets with patched separators
    """
    if not targets:
        return []

    def _patch_target(target):
        path = None
        parts = target.split(".")
        for i in range(0, len(parts)):
            m = parts[i]
            try:
                (fil, path, desc) = imp.find_module(m, [path] if path else None)
            except ImportError:
                fs_part = fs_glue.join(parts[:i])
                python_path = python_glue.join(parts[i:])
                return fs_part + fs_to_python_glue + python_path if python_path else fs_part
            if desc[2] == imp.PKG_DIRECTORY:
                # Package
                path = imp.load_module(m, fil, path, desc).__path__
        return target

    return map(_patch_target, targets)


def jb_start_tests():
    """
    Parses arguments, starts protocol and returns tuple of arguments

    :return: (string with path or None, list of targets or None, list of additional arguments)
    """

    # Handle additional args after --
    additional_args = []
    try:
        index = sys.argv.index("--")
        additional_args = sys.argv[index + 1:]
        del sys.argv[index:]
    except ValueError:
        pass
    parser = argparse.ArgumentParser(description='PyCharm test runner')
    parser.add_argument('--path', help='Path to file or folder to run')
    parser.add_argument('--target', help='Python target to run', action="append")
    namespace = parser.parse_args()
    del sys.argv[1:]  # Remove all args
    messages.TeamcityServiceMessages().message('enteredTheMatrix')
    return namespace.path, namespace.target, additional_args


def jb_doc_args(framework_name, args):
    """
    Runner encouraged to report its arguments to user with aid of this function

    """
    print("Launching {0} with arguments {1}".format(framework_name, " ".join(args)))
