# Relevance

Relevance is a smart tab organizer for Chrome, written in ClojureScript.   

It’ll create a natural arrangement where the tabs you have spent the longest on, which are expected to be the most relevant, are placed first, and the ones you haven’t read at all are shunted to the end of your list.

[You can read more about it here](https://numergent.com/relevance/).

This is Relevance 1.0.9.

# Building

## Development

To get a development build, run

```
lein chromebuild auto
```

You'll find the result in `target/unpacked/`, which you'll need to install into Chrome using developer mode.

## Release

To get the release version, run

```
lein clean
lein with-profile release chromebuild zip
```


# Testing

Relevance uses `doo` for running ClojureScript tests. I normally use `phantomjs`, but you can use it with the [environment of your choice](https://github.com/bensu/doo#setting-up-environments).

We can't test the entire application externally, since a lot of its API depend on Chrome functions being present (which only happens when you're running as a Chrome extension).  You can however test the general functions running:

```
lein with-profile test doo
```


# Development

## Continuous integration

I'm using [Gitlab CI](https://gitlab.com/ricardojmendez/relevance/pipelines) to test Relevance.

[![build status](https://gitlab.com/ricardojmendez/relevance/badges/develop/build.svg)](https://gitlab.com/ricardojmendez/relevance/commits/develop)

I've had some issues with Travis builds failing to get dependencies, so I decided to deprecate it.

## Process

I'm using [git-flow](http://nvie.com/posts/a-successful-git-branching-model/). Pull requests are welcome. Please base them off the `develop` branch.

## Version number conventions

Relevance uses [break versioning](https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md).

The development version on `project.clj` will reflect the current state of the code, and will normally include SNAPSHOT.

I can't update the package version from `manifest.json` to include alphanumerics, so that version will remain as the last public until I'm nearing a release (or need to test a data migration).


# License

Includes pixeden's [iOS 7 vector icons](http://themes-pixeden.com/font-demos/7-stroke/).

Relevance is (c) 2016 Numergent Limited, and released under the [MIT License](https://tldrlegal.com/license/mit-license).