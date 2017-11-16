# concrete5 Eclipse plugin

I've always been quite tired to explicitly tell Eclipse what's the result of `$app->make()` / `Core::make()` calls.

So, I wrote this plugin for Eclipse.

It still requires some fixes, but it's already very useful.

## Installation

1. Open Eclipse
2. Under the *Help* menu, choose *Install New Software...*
3. Click the *Add...* button and enter:
    1. Name: `concrete5 Update Site`
    2. Location: [`https://mlocati.github.io/concrete5-eclipse-plugin/updatesite`](https://mlocati.github.io/concrete5-eclipse-plugin/updatesite)
    3. Hit *OK*
4. In the *Work with* dropdown, select the update site that you just created
5. Write *concrete5* in the filter text box
6. Check the `concrete5 Plugin` and proceed with the installation

## Activation

The features of this plugin must be enabled on a per-project basis:
- in Eclipse, right-click the project node in the `Project Explorer` view
- go to the `Project Facets`
- check the `concrete5` option
