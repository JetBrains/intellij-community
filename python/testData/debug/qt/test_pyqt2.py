from PyQt5 import QtCore
import sys


class SomeObject(QtCore.QObject):

    finished = QtCore.pyqtSignal()

    def longRunning(self):
        for i in range(3):
            print("ping %d" % i)
        self.finished.emit()


app = QtCore.QCoreApplication([])
objThread = QtCore.QThread()
obj = SomeObject()
obj.moveToThread(objThread)
obj.finished.connect(objThread.quit)
objThread.started.connect(obj.longRunning)
objThread.finished.connect(app.exit)
objThread.start()
sys.exit(app.exec_())