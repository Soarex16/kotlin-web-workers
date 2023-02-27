## Motivation

Currently, the code generation process (IR to JS conversion) is fixed and cannot be modified by plugins. This on the one hand leaves flexibility for compiler developers, but at the same time does not allows to customize transformations at later stages of compilation.

For example, currently, there is no way to generate multiple .js files for one module. Such abilities a very useful in some scenarios like Web Workers API.
 
This PR aims to provide some basic implementation for such extension points.

## Safe harbour

It's also worth noting that the changes I'm suggesting don't take into account many edge cases. For example, incremental compilation, bundle size, etc.

## blah-blah

- codegen
        - надо научиться встраиваться в процесс ir -> js ast
            сейчас плагины умеют работать с ir, надо научить их работать с js ast
            (в PR добавить, что можно в дальнейшем тюнить платформенные представления кода)
        - постпроцессинг модулей для добавления importScripts() (в jvm есть постпроцессинг байткода)
    - IC
    - ide support