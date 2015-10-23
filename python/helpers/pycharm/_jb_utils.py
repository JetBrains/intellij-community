__author__ = 'Ilya.Kazakevich'
import sys


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
