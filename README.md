# Vocabulary Trainer

Android-приложение для заучивания английских слов карточками: интервальное
повторение, облачная синхронизация, друзья и статистика. Написано на Kotlin
и Jetpack Compose (Material 3). Интерфейс — на русском.

## Возможности

- **Карточки** — слово ↔ перевод, несколько переводов на карточку,
  необязательное описание.
- **Режимы** — Основной (слово → перевод), Реверс (перевод → слово),
  Комбо (детерминированное чередование по карточке).
- **Интервальное повторение** — упрощённый SM-2: «помню» удваивает интервал,
  «не помню» сбрасывает его.
- **Типы сессий** — по расписанию (до 50 карточек), N случайных (10–50) или
  все карточки как тренировка.
- **Облако** — Firebase Authentication (почта/пароль с обязательным
  подтверждением почты) и Cloud Firestore; слова и история едут за тобой на
  все устройства, работает офлайн.
- **Статистика** — сегодня, последние 7 дней, за всё время.
- **Друзья** — поиск по точному нику, заявки, просмотр публичного профиля
  друга и его друзей.
- **Аватарки** — выбор фото, кадрирование как в Telegram, полноэкранный
  просмотр с пинч-зумом, сохранение в галерею.

## Технологии

- Kotlin, Jetpack Compose (Material 3)
- Firebase Authentication + Cloud Firestore
- MVVM: один `AndroidViewModel` с набором `StateFlow`
- `minSdk` 24, `targetSdk`/`compileSdk` 36

## Сборка

Нужны Android SDK и JDK 21.

```bash
./gradlew assembleDebug
```

APK появится в `app/build/outputs/apk/debug/`. Либо открой проект в
Android Studio и запусти оттуда.

`assembleRelease` собирает release-APK, подписанный твоим ключом из
`keystore.properties`. Без этого файла APK подписывается debug-ключом.

## Настройка Firebase

`app/google-services.json` в репозиторий **не** закоммичен. Чтобы собрать под
свой бэкенд:

1. Создай проект в Firebase.
2. Добавь Android-приложение с пакетом `com.example.cardapp`.
3. Включи **Authentication → Email/Password**.
4. Создай базу **Cloud Firestore**.
5. Скачай `google-services.json` в папку `app/`
   (`app/google-services.json.example` показывает ожидаемую структуру).
6. Пропиши правила Firestore под модель данных приложения — рабочая заготовка:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Публичный профиль: читает любой вошедший, пишет только владелец.
    match /users/{uid} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == uid;

      // Полная аватарка (качается только при открытии просмотра).
      match /avatar/{doc} {
        allow read: if request.auth != null;
        allow write: if request.auth != null && request.auth.uid == uid;
      }

      // Приватные данные — только владелец.
      match /words/{id}  { allow read, write: if request.auth != null && request.auth.uid == uid; }
      match /events/{id} { allow read, write: if request.auth != null && request.auth.uid == uid; }

      // Входящие заявки в друзья: отправитель пишет в подколлекцию
      // получателя, получатель читает и удаляет.
      match /requests/{fromUid} {
        allow read, delete: if request.auth != null && request.auth.uid == uid;
        allow create:       if request.auth != null && request.auth.uid == fromUid;
      }

      // Рёбра дружбы: читает любой вошедший (чтобы смотреть друзей друга),
      // создают/удаляют только двое участников.
      match /friends/{friendUid} {
        allow read:           if request.auth != null;
        allow create, delete: if request.auth != null &&
                                 (request.auth.uid == uid || request.auth.uid == friendUid);
      }
    }

    // Реестр уникальных ников: занять свободный, удалить свой.
    match /nicknames/{nick} {
      allow read:   if request.auth != null;
      allow create: if request.auth != null && !exists(/databases/$(database)/documents/nicknames/$(nick));
      allow delete: if request.auth != null && resource.data.uid == request.auth.uid;
    }
  }
}
```

Storage и Cloud Functions не используются.

## Лицензия

[MIT](LICENSE)
