# DiffPerc

Calculates the percentage difference of words between two text files, ignoring
punctuation.

## Usage

The easiest way to run it is to download the binary under Releases:

```shell
$ diffperc base-file test-file
```

To run it from source:

```shell
$ bin/app base-file test-file
```

If you have Babashka you can run it this way:

```shell
$ bin/bb-app base-file test-file
```

To start developing in a REPL:

```shell
$ bin/dev
```

To build as a stand-alone binary using GraalVM:

```shell
$ bin/graalify
```

