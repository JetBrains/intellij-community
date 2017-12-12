# coding=utf-8

__author__ = 'Ilya.Kazakevich'
import fnmatch
import os
import sys


class FileChangesTracker(object):
    """
     On the instantiation the class records the timestampts of files stored in the folder.
     #get_changed_files() return the list of files that have a timestamp different from the one they had during the class instantiation


    """

    def __init__(self, folder, patterns="*"):
        self.old_files = self._get_changes_from(folder, patterns)
        self.folder = folder
        self.patterns = patterns

    def get_changed_files(self):
        assert self.folder, "No changes recorded"
        new_files = self._get_changes_from(self.folder, patterns=self.patterns)
        return filter(lambda f: f not in self.old_files or self.old_files[f] != new_files[f], new_files.keys())

    @staticmethod
    def _get_changes_from(folder, patterns):
        result = {}
        for tmp_folder, sub_dirs, files in os.walk(folder):
            sub_dirs[:] = [s for s in sub_dirs if not s.startswith(".")]
            if any(fnmatch.fnmatch(os.path.basename(tmp_folder), p) for p in patterns):
                for file in map(lambda f: os.path.join(tmp_folder, f), files):
                    try:
                        result.update({file: os.path.getmtime(file)})
                    except OSError:  # on Windows long path may lead to it: PY-23386
                        message = "PyCharm can't check if the following file been updated: {0}\n".format(str(file))
                        sys.stderr.write(message)
        return result


def jb_escape_output(output):
    """
    Escapes text in manner that is supported on Java side with CommandLineConsoleApi.kt#jbFilter
    Check jbFilter doc for more info

    :param output: raw text
    :return: escaped text
    """
    return "##[jetbrains{0}".format(output)


class OptionDescription(object):
    """
    Wrapper for argparse/optparse option (see VersionAgnosticUtils#get_options)
    """

    def __init__(self, name, description, action=None):
        self.name = name
        self.description = description
        self.action = action


class VersionAgnosticUtils(object):
    """
    "six" emulator: this class fabrics appropriate tool to use regardless python version.
    Use it to write code that works both on py2 and py3. # TODO: Use Six instead
    """

    @staticmethod
    def is_py3k():
        return sys.version_info >= (3, 0)

    @staticmethod
    def __new__(cls, *more):
        """
        Fabrics Py2 or Py3 instance based on py version
        """
        real_class = _Py3KUtils if VersionAgnosticUtils.is_py3k() else _Py2Utils
        return super(cls, real_class).__new__(real_class, *more)

    def to_unicode(self, obj):
        """

        :param obj: string to convert to unicode
        :return: unicode string
        """

        raise NotImplementedError()

    def get_options(self, *args):
        """
        Hides agrparse/optparse difference
        
        :param args:  OptionDescription
        :return: options namespace
        """
        raise NotImplementedError()


class _Py2Utils(VersionAgnosticUtils):
    """
    Util for Py2
    """

    def to_unicode(self, obj):
        if isinstance(obj, unicode):
            return obj
        try:
            return unicode(obj)  # Obj may have its own __unicode__
        except (UnicodeDecodeError, AttributeError):
            return unicode(str(obj).decode("utf-8"))  # or it may have __str__

    def get_options(self, *args):
        import optparse

        parser = optparse.OptionParser()
        for option in args:
            assert isinstance(option, OptionDescription)
            parser.add_option(option.name, help=option.description, action=option.action)
        (options, _) = parser.parse_args()
        return options


class _Py3KUtils(VersionAgnosticUtils):
    """
    Util for Py3
    """

    def to_unicode(self, obj):
        return str(obj)

    def get_options(self, *args):
        import argparse

        parser = argparse.ArgumentParser()
        for option in args:
            assert isinstance(option, OptionDescription)
            parser.add_argument(option.name, help=option.description, action=option.action)
        return parser.parse_args()
