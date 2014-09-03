import time
import sys

try:
    from PySide import QtCore
except:
    from PyQt4 import QtCore

# Subclassing QObject and using moveToThread
# http://labs.qt.nokia.com/2007/07/05/qthreads-no-longer-abstract/
class SomeObject(QtCore.QObject):

    finished = QtCore.Signal()

    def longRunning(self):
        count = 0
        while count < 5:
            time.sleep(.5)
            print "Increasing"
            count += 1
        self.finished.emit()

app = QtCore.QCoreApplication([])
objThread = QtCore.QThread()
obj = SomeObject()
obj.moveToThread(objThread)
obj.finished.connect(objThread.quit)
objThread.started.connect(obj.longRunning)
objThread.finished.connect(app.exit)
objThread.start()
app.exec_()
print('TEST SUCEEDED!')