# v1.17.2

Хотфикс журнала СПО:
- исправлена ошибка `net::ERR_CLEARTEXT_NOT_PERMITTED`;
- dekanat.bsuedu.ru после входа может перекидывать WebView на обычный `http://`;
- добавлен `network_security_config.xml`;
- для доменов `dekanat.bsuedu.ru` и `bsuedu.ru` разрешён cleartext HTTP;
- в Manifest добавлены:
  - `android:usesCleartextTraffic="true"`;
  - `android:networkSecurityConfig="@xml/network_security_config"`;
- сохранены фиксы v1.17.1 и функция оценок.

Известные баги:
- функция оценок тестовая. После проверки реальной таблицы может понадобиться доработка парсера.

Версия:
- versionCode = 45
- versionName = 1.17.2
