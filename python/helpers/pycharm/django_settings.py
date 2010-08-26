import os

settings_file = os.getenv('PYCHARM_DJANGO_SETTINGS_MODULE')
if not settings_file:
    settings_file = 'settings'

settings_module = __import__(settings_file)
components = settings_file.split('.')
for comp in components[1:]:
    settings_module = getattr(settings_module, comp)

for setting in dir(settings_module):
    if setting == setting.upper():
        globals()[setting] = getattr(settings_module, setting)

TEST_RUNNER = 'pycharm.django_test_runner.run_tests'

print "pycharm django settings imported" # XXX