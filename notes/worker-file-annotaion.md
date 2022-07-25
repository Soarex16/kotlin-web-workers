# Design notes: File annotation for workers

Данный документ описывает прототип поддержки веб-воркеров на основе аннотаций уровня файла.

Предлагается следующий способ использования веб-воркеров:

```kotlin
// file WebWorker.kt
package com.example.workers

@Target(AnnotationTarget.FILE)
annotation class WebWorker(val workerId: String)

// file DemoWorker.kt
@file:WebWorker("demo_worker")
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
    val worker = Worker("demo_worker")
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