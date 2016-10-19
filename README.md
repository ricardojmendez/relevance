# Relevance

Relevance is a smart tab organizer for Chrome, written in ClojureScript.   

It’ll create a natural arrangement where the tabs you have spent the longest on, which are expected to be the most relevant, are placed first, and the ones you haven’t read at all are shunted to the end of your list.

[You can read more about it here](https://numergent.com/relevance/).

This is Relevance 1.0.7

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
lein with-profile test doo phantom
```


# License

Includes pixeden's [iOS 7 vector icons](http://themes-pixeden.com/font-demos/7-stroke/).

Relevance is (c) 2016 Numergent Limited, and released under the [MIT License](https://tldrlegal.com/license/mit-license).