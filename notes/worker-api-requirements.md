# Design notes: Requirements for worker API

При разработке API необходимо учесть следующие:

- Стандарт [говорит](https://html.spec.whatwg.org/multipage/workers.html#scope-2), что создание Worker-а достаточно затратно. Это нужно учитывать и не создавать отдельный worker на каждую корутину (как это делается в некоторых библиотека для js, например, [greenlet](https://github.com/developit/greenlet))
- Каким образом мы будем обрабатывать исключения
- Как будем реализовывать покраску кода