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

## [greenlet](https://github.com/developit/greenlet)

Простая обертка над асинхронными функциями, которая формирует inline worker (`Function.prototype.toString()`) и осуществляет базовую коммуникацию - пересылка аргументов, результата и исключений as is. При отправке дополнительно фильтруются Transferable.

## [workerize](https://github.com/developit/workerize)

На вход принимает функцию в виде строки. Принцип работы аналогичен greenlet. Нет обработки Transferable.

В коммуникации предусмотрен случай, когда код представляет собой отдельный модуль - в таком случае в объект, который является результатом вызова `workerize('...')` проксирует вызовы всех экспортируемых методов в worker. Прокси функция запоминает continuation-ы Promise-а и отправляет запрос с уникальным идентификатором, соответствующий сохраненым колбекам.

Для данной библиотеки есть [плагин для wepack](https://github.com/developit/workerize-loader), который преобразует модули в воркеры если они импортированы следующим образом:

```js
import worker from 'workerize-loader!./some-module'
```

## [worker-farm](https://www.npmjs.com/package/worker-farm)

Для реализации многопоточности использует [child_process.fork](https://nodejs.org/api/child_process.html#child_processforkmodulepath-args-options), а не worker-ы.

## [threads](https://www.npmjs.com/package/threads)

TODO

## [comlimk](https://github.com/GoogleChromeLabs/comlink)

Поддерживает работу с [SharedWorker-ами](https://developer.mozilla.org/ru/docs/Web/API/SharedWorker) и  Transferable.

Для пересылки данных используются [`MessageChannel`](https://developer.mozilla.org/en-US/docs/Web/API/MessageChannel). По-умолчанию используется 2 - один для проксируемых данных и еще один для исключений.

Для проксирования используется стандартный [`Proxy`](https://developer.mozilla.org/ru/docs/Web/JavaScript/Reference/Global_Objects/Proxy). При этом прокси перехватывает следующие операции:
- get
- set
- apply
- construct

Остальные операции (такие как has, deleteProperty, defineProperty по каким-то причинам не реализованы).

Создаваемые объекты живут на стороне воркера и предоставляемый прокси реализует прозрачное взаимодействие с ними.