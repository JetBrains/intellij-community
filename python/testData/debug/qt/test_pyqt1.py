from PyQt5 import QtCore
import sys


class AThread(QtCore.QThread):

    def run(self):
        for i in range(3):
            print("ping %d" % i)

app = QtCore.QCoreApplication([])
thread = AThread()
thread.finished.connect(app.exit)
thread.start()
sys.exit(app.exec_())
