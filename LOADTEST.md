При старте обстрела с помощью утилиты `wrk` в логи сервера начало сыпаться много подобного:
```
java.nio.file.FileSystemException: /var/folders/k9/glw5gwh1689584mxqz2gcbz00000gp/T/highload-kv5842340380574953498/MTUyNDM=: Too many open files
        at sun.nio.fs.UnixException.translateToIOException(UnixException.java:91)
        at sun.nio.fs.UnixException.rethrowAsIOException(UnixException.java:102)
        at sun.nio.fs.UnixException.rethrowAsIOException(UnixException.java:107)
        at sun.nio.fs.UnixFileSystemProvider.newByteChannel(UnixFileSystemProvider.java:214)
        at java.nio.file.spi.FileSystemProvider.newOutputStream(FileSystemProvider.java:434)
        at java.nio.file.Files.newOutputStream(Files.java:216)
        at java.nio.file.Files.write(Files.java:3292)
        at ru.mail.polis.anar8888.KVFilesDao.upsert(KVFilesDao.java:43)
        at ru.mail.polis.anar8888.PutRequestProcessor.processDirectRequest(PutRequestProcessor.java:28)
        at ru.mail.polis.anar8888.AbstractRequestProcessor.process(AbstractRequestProcessor.java:27)
        at ru.mail.polis.anar8888.Service.entity(Service.java:68)
        at RequestHandler1_entity.handleRequest(Unknown Source)
        at one.nio.http.HttpServer.handleRequest(HttpServer.java:66)
        at one.nio.http.HttpSession.handleParsedRequest(HttpSession.java:144)
        at one.nio.http.HttpSession.processHttpBuffer(HttpSession.java:205)
        at one.nio.http.HttpSession.processRead(HttpSession.java:77)
        at one.nio.net.Session.process(Session.java:218)
        at one.nio.server.SelectorThread.run(SelectorThread.java:64)
java.io.IOException: one.nio.pool.PoolException: SocketPool[localhost:8081] createObject failed
        at ru.mail.polis.anar8888.AbstractRequestProcessor.proxiedPut(AbstractRequestProcessor.java:45)
        at ru.mail.polis.anar8888.PutRequestProcessor.processDirectRequest(PutRequestProcessor.java:30)
        at ru.mail.polis.anar8888.AbstractRequestProcessor.process(AbstractRequestProcessor.java:27)
        at ru.mail.polis.anar8888.Service.entity(Service.java:68)
        at RequestHandler1_entity.handleRequest(Unknown Source)
        at one.nio.http.HttpServer.handleRequest(HttpServer.java:66)
        at one.nio.http.HttpSession.handleParsedRequest(HttpSession.java:144)
        at one.nio.http.HttpSession.processHttpBuffer(HttpSession.java:205)
        at one.nio.http.HttpSession.processRead(HttpSession.java:77)
        at one.nio.net.Session.process(Session.java:218)
        at one.nio.server.SelectorThread.run(SelectorThread.java:64)
```
Вероятно, проблема в том, что мы создаем новый инстанс HttpClient на каждый поход в реплику. Попробуем сопоставить 
каждой реплике свой отдельный клиент. 

Все нормализовалось, ошибок нет:

### `wrk --latency -c1 -t1 -d1m -s put.lua http://localhost:8080`
```
Running 1m test @ http://localhost:8080
  1 threads and 1 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     5.56ms   25.81ms 280.43ms   95.82%
    Req/Sec     1.51k   299.30     1.76k    89.95%
  Latency Distribution
     50%  586.00us
     75%  658.00us
     90%    0.87ms
     99%  161.48ms
  88529 requests in 1.00m, 5.66MB read
Requests/sec:   1473.24
Transfer/sec:     96.39KB
```
Скорее всего, сторадж не сможет обрабатывать параллельные запросы из за одного клиента на каждую реплику. Проверим гипотезу:

### `wrk --latency -c2 -t2 -d1m -s put.lua http://localhost:8080`
```
Running 1m test @ http://localhost:8080
  2 threads and 2 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    70.36ms  275.70ms   1.99s    93.67%
    Req/Sec     1.01k   217.88     1.25k    82.75%
  Latency Distribution
     50%    0.91ms
     75%    1.06ms
     90%   18.29ms
     99%    1.63s
  100869 requests in 1.00m, 6.45MB read
  Socket errors: connect 0, read 0, write 0, timeout 4
Requests/sec:   1680.61
Transfer/sec:    109.96KB
```
Видимо, HttpClient внутри содержит пул соединений, что позволяет ему работать с несколькими потоками. Убеждаемся в этом,
посмотрев исходники.

Даже, добавив серверу worker'ов, никакого улучшения результатов не происходит, напротив, с двумя потоками было быстрее.

### `wrk --latency -c4 -t4 -d1m -s put.lua http://localhost:8080`
```
Running 1m test @ http://localhost:8080
  4 threads and 4 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency    84.18ms  257.06ms   1.72s    90.77%
    Req/Sec   485.05    122.60   710.00     79.49%
  Latency Distribution
     50%    1.97ms
     75%    2.38ms
     90%  273.63ms
     99%    1.31s
  96952 requests in 1.00m, 6.19MB read
  Socket errors: connect 0, read 0, write 0, timeout 4
Requests/sec:   1614.22
Transfer/sec:    105.62KB
```

Даже видны таймауты, что очень печально. Но на уровне сервера ошибок нет.

Можно предположить, что производительность не повышается из-за работы с файловой системой. Каждый кластер пытается
туда обращаться при каждом запросе, т.е. при 2-4 соединений к кластеру в файловую систему идет 6-12 обращений одновременно.

Посмотрим на скорость работы методов `get` и `delete` в режиме двух потоков. Для чистоты эксперимента сначала заполним 
сторадж значениями методом `put`, а затем проведем замеры при `get` и `delete`. Причем `put` будем проводить дольше, 
чтоб значения точно были в базе во время запросов `get` и `delete`.
### `wrk --latency -c2 -t2 -d5m -s put.lua http://localhost:8080`
```
Running 5m test @ http://localhost:8080
  2 threads and 2 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   144.49ms  355.76ms   1.99s    87.99%
    Req/Sec     1.06k   302.58     1.40k    85.07%
  Latency Distribution
     50%    0.87ms
     75%    1.28ms
     90%  665.86ms
     99%    1.53s
  473885 requests in 5.00m, 30.28MB read
  Socket errors: connect 0, read 0, write 0, timeout 6
Requests/sec:   1579.30
Transfer/sec:    103.33KB
```
### `wrk --latency -c2 -t2 -d1m -s get.lua http://localhost:8080`
```
Running 1m test @ http://localhost:8080
  2 threads and 2 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency     2.22ms   15.84ms 294.06ms   98.70%
    Req/Sec     1.54k   228.83     1.89k    73.57%
  Latency Distribution
     50%  678.00us
     75%  712.00us
     90%  746.00us
     99%   58.19ms
  182656 requests in 1.00m, 16.55MB read
Requests/sec:   3043.17
Transfer/sec:    282.33KB
```
### `wrk --latency -c2 -t2 -d1m -s delete.lua http://localhost:8080`
```
Running 1m test @ http://localhost:8080
  2 threads and 2 connections
  Thread Stats   Avg      Stdev     Max   +/- Stdev
    Latency   117.46ms  322.51ms   1.82s    89.28%
    Req/Sec     1.07k   315.29     1.61k    68.13%
  Latency Distribution
     50%    0.96ms
     75%    1.21ms
     90%  505.61ms
     99%    1.46s
  105366 requests in 1.00m, 6.83MB read
Requests/sec:   1754.37
Transfer/sec:    116.50KB
```
Ошибок нет, все хорошо, скорость обработки запросов приемлемая