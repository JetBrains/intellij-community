import time
import sys

try:
    from PySide import QtCore
except:
    from PyQt4 import QtCore

# Subclassing QThread
# http://doc.qt.nokia.com/latest/qthread.html
class AThread(QtCore.QThread):

    def run(self):
        count = 0
        while count < 5:
            time.sleep(.5)
            print("Increasing", count);sys.stdout.flush()
            count += 1

app = QtCore.QCoreApplication([])
thread = AThread()
thread.finished.connect(app.exit)
thread.start()
app.exec_()
print('TEST SUCEEDED!')