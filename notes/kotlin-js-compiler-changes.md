# Multiple output artifacts per module in Kotlin/JS

## Goals

Currently, there is no way to generate multiple .js files for one module.

There is currently no way to emit multiple .js files for a single module. The community needs this functionality because its absence makes it difficult to implement Kotlin/JS in the following scenarios:

* [Web Workers API](https://developer.mozilla.org/docs/Web/API/Web_Workers_API) requires workers-related code to be in a separate file. Also, because workers executes in a separate thread, programmers must use `importScripts(...)` to include dependencies in worker context.
* [Code splitting](https://webpack.js.org/guides/code-splitting/) and lazy loading. A common scenario in modern web applications is that some heavy component (Web IDE for ex.) should not load when not in use. Also in this case we want some guaranties from the compiler that we don't use declarations that haven't yet been loaded.
* Developing browser plugins using [WebExtensions API](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions) requires to have separate files for UI elements, background worker, etc.
* As [Roman Elizarom mentions](https://youtrack.jetbrains.com/issue/KT-6168/Ability-to-generate-one-JS-file-for-each-Kotlin-source-file#focus=Comments-27-1859600.0-0), in Kotlin/JVM for testing purposes you can have main in each .kt class.

## Acknowledgements of the problem

* https://youtrack.jetbrains.com/issue/KT-6168/Ability-to-generate-one-JS-file-for-each-Kotlin-source-file
* https://discuss.kotlinlang.org/t/can-i-have-several-output-files-when-using-kotlin-js/4266

## Known workarounds (working and not)

* You can use multi-module build and make module for each component. This aproach have several problems:
    
    This approach moves the problem from the compiler to the project model, thus cluttering it up. Also, according to users, this method is extremely inconvenient.

* You can use Kotlin Multiplatform to separate common code and make separate modules for specific targets (for ex. worker). 
    
    Due to the peculiarities of compilation of multiplatform projects, the bundle size will grow. This happens because, during compilation process, the compiler links between expected and actual modules into a single compilation unit.

* In case of workers, you can use [inline workers](https://web.dev/workers-basics/#inline-workers) technique.
    
    That won't work due to Kotlin's object representation. Also, this approach does not handle dependencies in any way.

## Edge cases to be taken into account

* Different module formats (UMD, ES, CJS, etc)
* Browser/NodeJS target
* Dead code elimination
* Incremental compilation
* Source maps generation
* Js/Klib artifact
* Dependency preserving between files without blowing-up bundle size

## Possible solutions

Currently, we have not come up with a universal solution that covers all of the above problems.