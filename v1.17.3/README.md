# v1.17.3

Хотфикс сборки:
- исправлена ошибка `method uriForApkFile(File) is already defined`;
- удалён дублирующий старый метод открытия APK;
- оставлен новый вариант через `content://...apkprovider/update.apk`.

Изменения v1.17.3:
- исправлена проблема, когда после скачивания APK открывался выбор файловых приложений, а не установщик пакетов;
- добавлен свой `ApkFileProvider` без AndroidX;
- APK теперь отдаётся установщику через `content://...apkprovider/update.apk`;
- установка открывается через `Intent.ACTION_INSTALL_PACKAGE`;
- сохранены фиксы v1.17.2 для журнала СПО и HTTP/Cleartext.

Версия:
- versionCode = 46
- versionName = 1.17.3
