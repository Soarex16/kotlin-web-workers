# Design notes: Worker as executor service

Предлагается реализовать отдельный 
[`CoroutineDispatcher`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/), который можно будет использовать в качестве менеджера задач, распределяемых между worker-ами.

Пример использования:

```kotlin
val context = newFixedWorkerContext(3, "worker-context")

launch(context) {
    delay(1000L)
    println("I'm executing in worker")
}
```

Как можно видеть, данное решение позволяет конфигурировать количество worker-ов в рантайме. Также это позволить реализовать поддержку [SharedWorker-ов](https://developer.mozilla.org/ru/docs/Web/API/SharedWorker).

Каким образом это можно реализовать? Как и в [KEEP](https://github.com/Kotlin/KEEP/blob/master/notes/web-workers.md) предлагается использовать раскраску кода и собирать все функции, которые будут исполняться в worker-е. Далее отдельно создаваемый контекст будет использоваться в качестве планировщика задач:

```kotlin
public abstract class WorkerCoroutineDispatcher: CoroutineDispatcher() {
    /** @suppress */
    @ExperimentalStdlibApi
    public companion object Key : AbstractCoroutineContextKey<CoroutineDispatcher, WorkerCoroutineDispatcher>(
        CoroutineDispatcher,
        { it as? WorkerCoroutineDispatcher })
}

public fun newFixedWorkerContext(workersCount: Int): WorkerCoroutineDispatcher =
    WorkerCoroutineDispatcherImpl(workersCount)

internal class WorkerCoroutineDispatcherImpl(private val workersCount: Int) : WorkerCoroutineDispatcher() {
    private val workers: List<Worker> = buildList {
        repeat(workersCount) {
            Worker(/* compiler inserts script url here */)
        }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        // some scheduling here
    }
}
```

Также есть [блогпост](https://avwie.github.io/multithreaded-web-applications-in-kotlin-with-web-workers), в котором описывается похожее решение, но более простое за счет того, что просто используются готовые библиотечные примитивы. 

Но тут есть один тонкий момент - worker можно завершить с помощью метода `terminate()`. Т. е. у него потенциально ограниченное время жизни. В случае если мы делаем свой планировщик необходимо либо запрещать завершать воркеры из общего пула, либо обеспечивать корутины соответствующим [скоупом](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-scope/).