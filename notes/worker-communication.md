# Design notes: Communication with workers

This design note describes how popular JS libraries performs communication with workers

## [kotlin-js-threads](https://github.com/Chainfire/kotlin-js-threads)

Общая схема работы напоминает [MPI](https://en.wikipedia.org/wiki/Message_Passing_Interface) когда главный и дочерние процессы используют один и тот же код, а разделение исполняемых задач осуществляется за счет флагов:

```js
if (!Thread.isMainThread { eval(it) }) return
```

Коммуникация реализована в виде посредника `EventReceiver` на принимающей стороне и с помощью `Worker.postMessage` на отправляющей стороне.

При пересылке объектов используется [`CopyCast`](https://github.com/Chainfire/kotlin-js-threads/blob/master/src/main/kotlin/eu/chainfire/kjs/threads/CopyCast.kt). Это необходимо из-за особенностей генерации кода в классах (по словам автора библиотеки используются дополнительные типовые проверки).

Библиотека поддерживает корутины, под капотом запускает их в `GlobalScope`. Таким образом, за счет того, что используется отдельный поток для воркера, главный поток не блокируется. В главный поток исключения передаются за счет [`coroutineExceptionHandler()`](https://github.com/Chainfire/kotlin-js-threads/blob/master/src/main/kotlin/eu/chainfire/kjs/threads/Thread.kt#L647), который пересылает их с помощью механизма коммуникации.

Учитывая то, что для запуска воркеров используется тот же скрипт, что и для главного потока, то при компиляции можно встроить эти проверки. Это позволит не делать code coloring.