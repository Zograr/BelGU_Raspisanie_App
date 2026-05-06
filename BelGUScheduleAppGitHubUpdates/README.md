# BelGUScheduleApp

Версия 1.9.

## GitHub Releases / обновления

В приложении добавлена кнопка **Обновления**.

Перед сборкой замени в файле:

```text
app/src/main/res/values/strings.xml
```

```xml
<string name="github_owner">YOUR_GITHUB_USERNAME</string>
<string name="github_repo">BelGUScheduleApp</string>
```

на свои данные GitHub.

Пример:

```xml
<string name="github_owner">zograr</string>
<string name="github_repo">BelGUScheduleApp</string>
```

Чтобы обновления работали:
1. Собери APK.
2. Загрузи проект на GitHub.
3. Создай Release с тегом выше версии приложения, например `v1.10`.
4. Прикрепи APK к Release.
5. В приложении нажми **Обновления**.

Важно: новый APK должен быть подписан тем же ключом, что и установленная версия.
