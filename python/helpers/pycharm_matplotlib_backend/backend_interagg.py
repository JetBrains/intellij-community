import base64
import matplotlib
import os
import sys

from matplotlib._pylab_helpers import Gcf
from matplotlib.backend_bases import FigureManagerBase, ShowBase
from matplotlib.backends.backend_agg import FigureCanvasAgg
from matplotlib.figure import Figure

from datalore.display import display

PY3 = sys.version_info[0] >= 3

index = int(os.getenv("PYCHARM_MATPLOTLIB_INDEX", 0))

rcParams = matplotlib.rcParams


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
        display(DisplayDataObject(plot_index, width, buffer))

    def draw(self):
        FigureCanvasAgg.draw(self)
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


class DisplayDataObject:
    def __init__(self, plot_index, width, image_bytes):
        self.plot_index = plot_index
        self.image_width = width
        self.image_bytes = image_bytes

    def _repr_display_(self):
        image_bytes_base64 = base64.b64encode(self.image_bytes)
        if PY3:
            image_bytes_base64 = image_bytes_base64.decode()
        body = {
            'plot_index': self.plot_index,
            'image_width': self.image_width,
            'image_base64': image_bytes_base64
        }
        return ('pycharm-plot-image', body)
