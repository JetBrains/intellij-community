#!/usr/bin/perl

use strict;

# cwd should be $TM_DIRECTORY
# filename to check is $ARGV[0]

my $file = $ARGV[0];

my %file_source;
sub read_source {
    require File::Spec;
    require File::Basename;
    my $file = shift;
    my $file_source;
    { local $/ = undef; open F, "<$ENV{TM_FILEPATH}"; $file_source = <F>; close F }
    my @file_source = split /\r?\n/, $file_source;
    my $path = $file;
    if (!-f $path || !File::Spec->file_name_is_absolute($path)) {
        $path = File::Spec->rel2abs($path, $ENV{TM_DIRECTORY});
        if (!-e $path) {
            $path = undef;
            my $base = File::Basename::basename($path);
            foreach (@INC) {
                my $file = File::Spec->catfile($_, $base);
                $path = $file, last if -e $file;
            }
        }
    }
    $file_source{$file} = { source => \@file_source, path => $path };
}

my @lines = `"\${TM_PERL:-perl}" -Ilib -cw "$file" 2>&1`;

my $lines = join '', @lines;

if ((scalar(@lines) == 1) && ($lines =~ m/ syntax OK$/s)) {
    exit 0;
}

$lines =~ s/&/&amp;/g;
$lines =~ s/</&lt;/g;
$lines =~ s/>/&gt;/g;

# link line numbers to source
$lines =~ s%^((?:.+)[ ]+at[ ]+(.+)[ ]+line[ ]+)(\d+)[.,]%
    my $pre = $1;
    my $file = $2;
    my $lnum = $3;
    my $col;
    if ($pre =~ m/"([^"]+)"/) {
        read_source($file)
            unless exists $file_source{$file};
        my $source_line = $file_source{$file}{source}[$lnum-1];
        $file = $file_source{$file}{path};
        $col = index($source_line, $1);
        $col = $col != -1 ? $col + 1 : 0;
    } else {
        if ($file !~ m!^/!) {
            read_source($file)
                unless exists $file_source{$file};
            $file = $file_source{$file}{path};
        }
    }
    my $url = qq{txmt://open?url=file://$file&amp;line=$lnum};
    $url .= "&amp;column=$col" if $col;
    qq{$pre<a href="$url">$lnum</a>.};
%gmex;

my $output = '<pre style="word-wrap: break-word;">';
$output .= $lines;
$output .= '</pre>';

print $output;