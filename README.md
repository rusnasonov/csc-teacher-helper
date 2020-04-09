# Computer Science Center Teacher Helper

Небольшая программа-помощник для преподавателя [CSC](https://compscicenter.ru/). Делает отчеты по студентам:
* кому надо ответить
* от кого ожидается ответ
* кому надо назначить преподавателя
* кто еще ничего не сдал
* кто уже все сдал

## Как запускать

`CSC_SESSION_ID="xxx" CSC_ME="ФИО" java -jar csc_teacher_helper-all.jar.jar 123
`

Где:

* CSC_SESSION_ID — кука `cscsessionid` c https://my.compscicenter.ru
* CSC_ME — ФИО препрдавателя, как отражается в комментариях задания
* 123 — номер задания, найти можно в урле https://my.compscicenter.ru/teaching/assignments/123/


## Как собрать

Собрать можно из исходников командой `./gradlew shadowJar`.

