# Design notes: File annotation for workers

Данный документ описывает прототип поддержки веб-воркеров на основе аннотаций уровня файла.

Предлагается использовать подход из JavaScript и считать воркером файл со своей точкой входа. Для этого предлагается ввести аннотацию `@WebWorker` уровня файла.

В процессе компиляции файлы, отмеченные аннотацией `@WebWorker` будут транслироваться в отдельный js файл.

Возможный способ использования аннотаций веб-воркеров:

```kotlin
// file WebWorker.kt
package com.example.workers

@Target(AnnotationTarget.FILE)
annotation class WebWorker

// file DemoWorker.kt
@file:WebWorker
package com.example.workers.worker

import com.example.workers.CountMessage
import com.example.workers.WebWorker
import org.w3c.dom.DedicatedWorkerGlobalScope
import org.w3c.dom.MessageEvent

external val self: DedicatedWorkerGlobalScope

var count: Int = 1

fun main() {
    self.addEventListener("message", { event ->
        if (event !is MessageEvent) return@addEventListener

        when (event.data) {
            "inc" -> count += 1
            "dec" -> count -= 1
        }
        self.postMessage(CountMessage(count))
    })
}

// file Model.kt
package com.example.workers

interface WorkerMessage {
    val type: String
}

data class CountMessage(val count: Int, override val type: String = "count") : WorkerMessage

// file Main.kt
package com.example.workers

import kotlinx.coroutines.*
import org.w3c.dom.MessageEvent
import org.w3c.dom.Worker
import kotlin.random.Random

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    val worker = Worker("./demo_worker.kt")
    worker.addEventListener("message", { event ->
        if (event !is MessageEvent) return@addEventListener

        val payload = event.data
        if (payload !is CountMessage) return@addEventListener

        println("count = ${payload.count}")
    })

    GlobalScope.launch {
        repeat(10) {
            val message = if (Random.nextBoolean()) "inc" else "dec"
            worker.postMessage(message)
            delay(1000)
        }
    }
}
```

Открытый вопрос: как идентифицировать worker-ы? Предлагаются следующие варианты:
1. Использовать имя файла, отмеченного аннотацией `@WebWorker`. Тогда вызовы конструктора воркера будут выглядеть следующим образом:

    ```kotlin
    val worker = Worker("../worker/demo_worker.kt")
    ```

    В процессе компиляции данный путь может изменяться, поэтому необходим дополнительный проход по IR дереву для замены путей к скрипту.

    К преимуществам данного решения можно отнести простоту реализации диагностик и прочих проверок со стороны компилятора и IDE.

    Недостатком является зависимость от структуры папок, которая [не обязательно](https://kotlinlang.org/docs/coding-conventions.html#directory-structure) соответствует структуре пакетов.

2. Добавить в аннотацию `@WebWorker` в качестве идентификатора параметр `workerId: String`. Тогда при вызове конструктора можно использовать этот идентификатор для обращения:

    ```kotlin
    // file WebWorker.kt
    package com.example.workers

    @Target(AnnotationTarget.FILE)
    annotation class WebWorker(val workerId: String)

    // file DemoWorker.kt
    @file:WebWorker("worker_foo")
    package com.example.workers.worker
    ...

    // file Main.kt
    fun main() {
        val worker = Worker("worker_foo")
        ...
    }
    ```

    Данный подход позволяет избежать зависимости от файловой структуры проекта.

    С другой стороны трансформацию необходимо осуществлять в 2 прохода:

    1. На первом этапе необходимо найти все файлы, отмеченные аннотацией `@WebWorker` и собрать их в ассоциативный массив `workerId -> File`

    2. Во время второго прохода происходит поиск вызовов `Worker`. Когда компилятор встречает вызов `Worker(workerId)` он осуществляет подстановку на основе ассоциативного массива, построенного на предыдущем шаге.

    Данный вариант предполагает, что в конструктор `Worker` передается невалидный URL-адрес скрипта. Данная ситуация может потенциально восприниматься ошибочной инструментами статического анализа и вызывать недопонимание со стороны разработчиков. В качестве решения данной проблемы можно ввести тип-маркер следующего вида:

    ```kotlin
    class KtWorker(val workerId: String): Worker(...)
    ```

    Также необходимо учесть, что при использовании такого решения маркерные типы необходимо будет вводить также и для SharedWorker-ов.

Дополнительно в процессе компиляции осуществляется проверка, что в файлах с аннотацией `@WebWorker` не происходит обращения к DOM.