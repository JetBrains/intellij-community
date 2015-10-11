import sys
import optparse
from django_manage_commands_provider import _xml


class Option:
    def __init__(self):
        self.long = []
        self.short = []
        self.arg = None
        self.help = None

    def dump(self, dumper):
        dumper.add_command_option(self.long, self.short, self.help, self.arg)


def parse_option_desc(option_desc):
    option = Option()
    option.short = option_desc._short_opts
    option.long = option_desc._long_opts
    option.help = option_desc.help
    if option_desc.nargs > 0:
        option.arg = (option_desc.nargs, option_desc.type)
    return option


def get_options(options_parser):
    return map(parse_option_desc, options_parser.option_list)


def dump_actions(dumper, app):
    common_options = get_options(app._GetOptionParser())

    for name, action in app.actions.iteritems():
        dumper.start_command(name, action.short_desc)

        args = action.usage.split(name.split(' ')[0])[-1].strip()
        dumper.set_arguments(args)

        for option in common_options:
            option.dump(dumper)

        if action.options:
            parser = optparse.OptionParser(conflict_handler='resolve')
            action.options(app, parser)
            for option in get_options(parser):
                option.dump(dumper)
        dumper.close_command()


if __name__ == "__main__":
    sys.path.append(sys.argv[1])
    import appcfg

    try:
        appcfg.run_file('appcfg.py', globals())
    finally:
        app = AppCfgApp(['appcfg.py', 'help'])
        dumper = _xml.XmlDumper()
        dump_actions(dumper, app)
        print(dumper.xml)
        sys.exit(0)
