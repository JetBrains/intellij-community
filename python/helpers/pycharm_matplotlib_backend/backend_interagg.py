import matplotlib
import os
import socket
import struct
from matplotlib._pylab_helpers import Gcf
from matplotlib.backend_bases import FigureManagerBase, ShowBase
from matplotlib.backends.backend_agg import FigureCanvasAgg
from matplotlib.figure import Figure

HOST = 'localhost'
PORT = os.getenv("PYCHARM_MATPLOTLIB_PORT")
PORT = int(PORT) if PORT is not None else None
PORT = PORT if PORT != -1 else None
index = int(os.getenv("PYCHARM_MATPLOTLIB_INDEX", 0))

rcParams = matplotlib.rcParams
verbose = matplotlib.verbose


class Show(ShowBase):
    def __call__(self, **kwargs):
        managers = Gcf.get_all_fig_managers()
        if not managers:
            return

        for manager in managers:
            manager.show(**kwargs)

    def mainloop(self):
        pass


show = Show()


# from pyplot API
def draw_if_interactive():
    if matplotlib.is_interactive():
        figManager = Gcf.get_active()
        if figManager is not None:
            figManager.canvas.show()


# from pyplot API
def new_figure_manager(num, *args, **kwargs):
    FigureClass = kwargs.pop('FigureClass', Figure)
    figure = FigureClass(*args, **kwargs)
    return new_figure_manager_given_figure(num, figure)


# from pyplot API
def new_figure_manager_given_figure(num, figure):
    canvas = FigureCanvasInterAgg(figure)
    manager = FigureManagerInterAgg(canvas, num)
    return manager


# from pyplot API
class FigureCanvasInterAgg(FigureCanvasAgg):
    def __init__(self, figure):
        FigureCanvasAgg.__init__(self, figure)

    def show(self):
        self.figure.tight_layout()
        FigureCanvasAgg.draw(self)
        if PORT is None:
            return

        if matplotlib.__version__ < '1.2':
            buffer = self.tostring_rgb(0, 0)
        else:
            buffer = self.tostring_rgb()

        if len(set(buffer)) <= 1:
            # do not plot empty
            return

        render = self.get_renderer()
        width = int(render.width)

        plot_index = index if os.getenv("PYCHARM_MATPLOTLIB_INTERACTIVE", False) else -1
        try:
            sock = socket.socket()
            sock.connect((HOST, PORT))
            sock.send(struct.pack('>i', width))
            sock.send(struct.pack('>i', plot_index))
            sock.send(struct.pack('>i', len(buffer)))
            sock.send(buffer)
        except OSError as _:
            # nothing bad. It just means, that our tool window doesn't run yet
            pass

    def draw(self):
        is_interactive = os.getenv("PYCHARM_MATPLOTLIB_INTERACTIVE", False)
        if is_interactive and matplotlib.is_interactive():
            self.show()


class FigureManagerInterAgg(FigureManagerBase):
    def __init__(self, canvas, num):
        FigureManagerBase.__init__(self, canvas, num)
        global index
        index += 1
        self.canvas = canvas
        self._num = num
        self._shown = False

    def show(self, **kwargs):
        self.canvas.show()
        Gcf.destroy(self._num)
