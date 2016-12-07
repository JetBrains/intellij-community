# coding=utf-8
__author__ = 'Ilya.Kazakevich'
import sys, os

class ChangesMon(object):
    """
    Engine to track changes. Create it with directory, then change some files and call #get_changed_files
    To get list of changed files
    """
    def __init__(self, folder):
        self.old_files = self._get_changes_from(folder)
        self.folder = folder

    def get_changed_files(self):
        assert self.folder, "No changes recorded"
        new_files = self._get_changes_from(self.folder)
        return filter(lambda f: f not in self.old_files or self.old_files[f] != new_files[f], new_files.keys())

    @staticmethod
    def _get_changes_from(folder):
        result = {}
        for tmp_folder, _, files in os.walk(folder):
            for file in map(lambda f: os.path.join(tmp_folder, f), files):
                result.update({file: os.path.getmtime(file)})
        return result


def jb_escape_output(output):
    """
    Escapes text in manner that is supported on Java side with CommandLineConsoleApi#jbFilter
    Check jbFilter doc for more info

    :param output: raw text
    :return: escaped text
    """
    return "##[jetbrains{0}".format(output)


class VersionAgnosticUtils(object):
    """
    "six" emulator: this class fabrics appropriate tool to use regardless python version.
    Use it to write code that works both on py2 and py3. # TODO: Use Six instead
    """

    @staticmethod
    def __new__(cls, *more):
        """
        Fabrics Py2 or Py3 instance based on py version
        """
        real_class = _Py3KUtils if sys.version_info >= (3, 0) else _Py2Utils
        return super(cls, real_class).__new__(real_class, *more)

    def to_unicode(self, obj):
        """

        :param obj: string to convert to unicode
        :return: unicode string
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
            return unicode(obj) # Obj may have its own __unicode__
        except (UnicodeDecodeError, AttributeError):
            return unicode(str(obj).decode("utf-8")) # or it may have __str__


class _Py3KUtils(VersionAgnosticUtils):
    """
    Util for Py3
    """

    def to_unicode(self, obj):
        return str(obj)
