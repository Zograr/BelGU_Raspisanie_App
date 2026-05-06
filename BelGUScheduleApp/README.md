# Расписание БелГУ

Простое Android-приложение для группы 90002595.

При открытии приложение само загружает расписание с сайта БелГУ и показывает только сегодняшний день.
Если интернет пропал, оно показывает последнюю сохранённую версию.

## Как собрать APK

1. Открой проект в Android Studio.
2. Дождись Gradle Sync.
3. Нажми `Build -> Generate App Bundles or APKs -> Generate APKs`.

Либо через Terminal:

```bat
.\gradlew.bat assembleDebug
```

Готовый APK будет здесь:

```text
app\build\outputs\apk\debug\app-debug.apk
```

## Где поменять группу или сайт

Файл:

```text
app/src/main/res/values/strings.xml
```

Строка:

```xml
<string name="schedule_url">https://bsuedu.ru/bsu/education/schedule/groups/index.php?group=90002595</string>
```
