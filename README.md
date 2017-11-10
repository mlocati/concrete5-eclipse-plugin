# concrete5 Eclipse plugin

I've always been quite tired to explicitly tell Eclipse what's the result of `$app->make()` / `Core::make()` calls.

So, I wrote this plugin for Eclipse.

It still requires some fixes, but it's already very useful.

## Installation

At the moment, the only way to install the plugin is to go to the [releases](https://github.com/mlocati/concrete5-eclipse-plugin/releases) page, download the latest .jar version and copy it to the Eclipse `plugins` directory.

## Activation

The features of this plugin must be enabled on a per-project basis:
- in Eclipse, right-click the project node in the `Project Explorer` view
- go to the `Project Facets`
- check the `concrete5` option
