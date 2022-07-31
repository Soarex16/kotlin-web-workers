# Design notes: Multiple entrypoint workers

В данном документе представлена альтернативная модель компиляции кода, использующего Web Worker-ы. Предложенное решение расширяет [оригинальный документ](https://github.com/Kotlin/KEEP/blob/master/notes/web-workers.md).

Оригинальный proposal имеет ряд проблем:
- При компиляции на каждый вызов интринсика `worker` создается отдельный файл, который содержит код, находящийся в лямбде, передаваемой в `worker` и ее зависимости. Это увеличивает размер итогового бандла если не выносить общие зависимости скриптов в отдельные файлы.
- Code coloring - это сложно. Необходимо учитывать инкрементальную компиляцию и т. д.

Описываемый подход основан на следующей [идее](https://stackoverflow.com/a/10136565).

Далее для простоты будет рассмотрен случай использование одного Worker-а.

Предположим, что в проекте имеется несколько вызовов интринсик функции:

```kotlin
// file1.kt
import some.crypto.library

suspend fun createBlob(file: File): FileBlob {
    val fileContent = FileReader.readAsArrayBuffer(file)
    val hash = worker { // <<< suspension point, will become worker$1
        // compute hash based on fileContent
        val hash: String = ...
        hash
    }
    return FileBlob(file.name, hash)
}

// file2.kt
import video.manipulation.tools

suspend fun getClipFromVideo(url: String, from: Int, to: Int): File {
    // fetch data, etc
    val videoFragment = worker { // <<< suspension point, will become worker$2
        // decode video, cut fragment
        val fragment: ArrayBuffer
        fragment
    }
    // make file from ArrayBuffer
    return clip
}
```

Вызов функции worker в процессе компиляции будет заменяться следующим образом:

```kotlin
val a: Int = ...
val b: String = ...
worker { // type of lambda DedicatedWorkerGlobalScope.() -> String
    // some code, that uses a and b
    "returned value"
}

// will be transformed to
val a: Int = ...
val b: String = ...
val $closure = Closure$<SOME_LAMBDA_ID>(a, b)
runInWorker("worker$<SOME_LAMBDA_ID>", $closure, String.class)
```

Где функция `runInWorker` определяется следующим образом:
```kotlin
suspend fun <T : Any> runInWorker(functionName: String, closure: Any, returnType: KClass<T>): T {
    // send task identified by functionName + closure to worker and waits for result
    // returnType used only for type inference and is likely redundant

    // Each call must also have a unique ID, since the Worker does 
    // not guarantee the order in which the function executed. 
    // ID helps to understand which result corresponds to which function call.
}
```

В процессе компиляции формируется один файл бандла, который содержит несколько точек входа - для Worker-ов и для главного потока:

```js
(function(global) {
    var is_main_thread = this.document;
    var script_path = document.currentScript.src;
    function worker_handler(e) {
        // event handler for parent -> worker messages
        switch(e.data.$kind) {
            case 'worker$<SOME_LAMBDA_ID_1>':
                {
                    // compute hash based on fileContent
                    postMessage({
                        $task_id: e.data.$task_id,
                        $kind: "RETURN",
                        $result: hash,
                    });
                    return;
                }
            case 'worker$<SOME_LAMBDA_ID_2>':
                {
                    // decode video, cut fragment
                    postMessage({
                        $task_id: e.data.$task_id,
                        $kind: "RETURN",
                        $result: fragment,
                    });
                    return;
                }
        }
    }
    function new_worker() {
        // setup infrastructure for task schedulling, etc
        var worker = new Worker(script_path);

        // TODO: не аккуратно описан механизм взаимодействия с worker-ом

        var id = 1;
        var results = new WeakMap(); // should we use WeakMap here?
        function parent_handler(e) {
            // event handler for worker -> parent messages

            // as a possible implementation of this handler, 
            // results of tasks can be putted into map with $task_id as a key

            results.set(e.$task_id, e.$retult);
        }
        worker.addEventListener('message', parent_handler, false);

        window.runInWorker = function(functionName, closure) {
            worker.postMessage({
                $task_id: id++,
                $kind: functionName,
                $closure: closure,
            });
        }
    }
    if (is_main_thread) {
        new_worker();
        // rest of the code

        // somewhere in code
        var a = ...
        var b = ...
        var $closure = { a, b };
        window.runInWorker("worker$<SOME_LAMBDA_ID>", $closure);
    } else {
        global.addEventListener('message', worker_handler, false);
    }
})(this);
```

Стоит отметить, что описанный подход масштабируется на случай использования нескольких Worker-ов. Для этого достаточно ввести промежуточную сущность - scheduler, который будет распределять нагрузку между Worker-ами. Это можно сделать не меняя модель компиляции с помощью внесения модицикации в suport lib.