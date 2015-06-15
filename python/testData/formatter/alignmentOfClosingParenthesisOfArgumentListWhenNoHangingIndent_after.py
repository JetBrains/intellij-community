option_list = BaseCommand.option_list + (
    make_option('-d', '--db',
                dest='db',
                metavar='DB_ID',
                help='Mandatory: DATABASES setting key for database'),
    make_option('-p', '--project',
                dest='project',
                default='UNGA',
                metavar='PROJECT_LABEL',
                help=('Project to use (can specify "any"). '
                      'Default: "UNGA"'),
                ),
    make_option('-b', '--batch',
                dest='batch',
                default='UNGA',
                metavar='BATCH_LABEL',
                help=('Batch to use (can specify "any").  '
                      'Default: "UNGA"'),
                ),
)
