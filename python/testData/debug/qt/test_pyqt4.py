from PyQt5 import QtCore


class SomeObject(QtCore.QObject):

    signal = QtCore.pyqtSignal()

    def __init__(self):
        super().__init__()


def boom():
    print(wrong_arg)


obj = SomeObject()
obj.signal.connect(boom)
obj.signal.emit()
