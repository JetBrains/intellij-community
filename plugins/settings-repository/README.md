# Configuration

Use File -> Settings Repository... to configure.

Specify URL of upstream Git repository. Please note — ssh is not yet supported. Please use HTTP/HTTPS. File URL is supported, you will be prompted to init repository if specified path is not exists or repository is not created.
[GitHub](www.github.com) could be used to store settings.

Check "Update repository from remote on start" if you want automatically update your configuration on IDE start. Otherwise you can use Vcs -> Sync Settings...

On first sync you will be prompted to specify login/password. In case of GitHub strongly recommended to use an [access token](https://help.github.com/articles/creating-an-access-token-for-command-line-use). Do not use your login/password. Leave password empty if you use token instead of login. Bitbucket [doesn't support tokens](https://bitbucket.org/site/master/issue/7735), so, we don't recommend use it to store settings.