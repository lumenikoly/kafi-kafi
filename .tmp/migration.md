# Kafi Kafi — техническое задание на переписывание приложения

## 1. Цель проекта

Переписать существующий Kafi Kafi с Kotlin/JVM + Compose Desktop на:

- Tauri 2;
- Rust;
- rust-rdkafka;
- Tokio;
- React;
- TypeScript;
- Vite;
- TanStack Table;
- TanStack Virtual.

Результат должен быть быстрым, компактным, кроссплатформенным настольным Kafka-клиентом с богатым интерфейсом и простой поддерживаемой архитектурой.

Основной приоритет:

1. высокая отзывчивость интерфейса;
2. низкое потребление ресурсов;
3. небольшой размер дистрибутива;
4. полноценная работа с Kafka;
5. удобное дальнейшее развитие;
6. отсутствие отдельного backend-сервера;
7. локальное хранение профилей и настроек;
8. безопасная работа с учётными данными.

Переписывание не должно быть механическим переносом Kotlin-кода на Rust. Необходимо сохранить полезную архитектуру и пользовательские сценарии, но устранить недостатки текущей реализации.

---

# 2. Функциональная совместимость

Новая версия должна как минимум сохранить возможности текущего Kafi Kafi.

## 2.1. Подключения

Пользователь должен иметь возможность:

- создавать несколько профилей Kafka;
- изменять профиль;
- удалять профиль;
- тестировать соединение до сохранения;
- подключаться к выбранному профилю;
- переключаться между профилями;
- видеть состояние соединения;
- видеть понятную ошибку подключения.

Поддерживаемые протоколы:

- `PLAINTEXT`;
- `SSL`;
- `SASL_PLAINTEXT`;
- `SASL_SSL`.

Для SASL обязательно поддержать:

- `PLAIN`;
- `SCRAM-SHA-256`;
- `SCRAM-SHA-512`.

Дополнительные механизмы, включая GSSAPI и OAUTHBEARER, не должны блокировать архитектуру, но могут быть реализованы после базового набора.

Необходимо поддерживать дополнительные безопасные Kafka properties для расширенных случаев.

Системные свойства, управляемые отдельными полями интерфейса, не должны одновременно переопределяться через произвольные properties.

---

# 3. Работа с кластером

После подключения приложение должно показывать:

- cluster ID;
- controller;
- список brokers;
- broker ID;
- host;
- port;
- rack;
- состояние соединения.

Должна быть ручная команда Refresh.

Автоматические фоновые обновления не должны создавать постоянную избыточную нагрузку на Kafka.

---

# 4. Topics

## 4.1. Список

Экран Topics должен поддерживать:

- получение списка topics;
- поиск;
- скрытие/показ внутренних topics;
- количество partitions;
- сортировку;
- виртуализацию при большом количестве строк;
- ручное обновление.

Интерфейс не должен создавать тысячи DOM-узлов для большого кластера.

## 4.2. Создание topic

Поля:

- name;
- partitions;
- replication factor;
- дополнительные config properties.

Перед созданием выполняется клиентская и серверная валидация.

После успешного создания список обновляется.

## 4.3. Topic detail

Для topic открыть отдельную вкладку рабочего пространства.

Разделы:

- Messages;
- Partitions;
- Configuration.

## 4.4. Partitions

Показывать:

- partition ID;
- leader;
- replicas;
- ISR;
- earliest offset;
- latest offset.

## 4.5. Configuration

Показывать Kafka configuration topic.

Для каждого параметра:

- имя;
- значение;
- значение по умолчанию;
- read-only;
- sensitive.

Поддержать изменение разрешённых параметров.

Sensitive values не должны попадать в журнал приложения.

---

# 5. Просмотр сообщений

Это наиболее чувствительная к производительности часть приложения.

## 5.1. Позиция чтения

Пользователь может начать чтение:

- Latest;
- Earliest;
- Specific offset;
- Timestamp.

Можно выбрать:

- все partitions;
- одну конкретную partition.

## 5.2. Управление consumer

Доступны:

- Start;
- Pause;
- Resume;
- Stop.

Закрытие вкладки должно немедленно останавливать принадлежащий ей consumer.

Переключение Kafka-профиля должно корректно остановить consumers старого подключения.

## 5.3. Consumer не должен изменять пользовательские группы

Обычный режим просмотра сообщений является инспекционным режимом.

По умолчанию:

- `enable.auto.commit = false`;
- используется временный технический consumer group ID;
- offsets пользовательских consumer groups не изменяются.

---

# 6. Архитектура потока сообщений

Нельзя передавать каждое Kafka-сообщение отдельным Tauri event.

Правильный поток:

```text
Kafka
  ↓
rust-rdkafka
  ↓
Consumer task
  ↓
bounded message buffer
  ↓
batcher
  ↓
Tauri Channel
  ↓
frontend session store
  ↓
TanStack Virtual
```

## 6.1. Пакетная доставка

Сообщения передаются интерфейсу пакетами.

Пакет отправляется при выполнении одного из условий:

- накоплено ориентировочно 100–250 записей;
- прошло ориентировочно 16–50 мс с предыдущей отправки.

Конкретные значения должны быть вынесены в одну внутреннюю конфигурацию и подобраны по benchmark, а не распределены магическими числами по коду.

## 6.2. Ограниченный буфер

Каждая consumer session должна иметь ограниченный буфер сообщений.

Использовать кольцевую очередь / `VecDeque`.

При достижении лимита:

- старые записи удаляются;
- приложение не увеличивает память бесконечно;
- интерфейс получает информацию о количестве удалённых из отображения сообщений.

Буфер должен быть настраиваемым.

## 6.3. Каноническое хранение

Полные payload сообщений должны оставаться на стороне Rust.

React не должен постоянно хранить вторую полную копию всех Kafka payload.

В WebView передаётся представление строки:

```text
MessageRow {
    id
    partition
    offset
    timestamp
    keyPreview
    valuePreview
    keySize
    valueSize
    valueType
    headersCount
}
```

Полное содержимое загружается только при выборе сообщения.

Это необходимо для:

- снижения расхода памяти;
- уменьшения IPC;
- снижения нагрузки на React;
- нормальной работы с крупными Kafka records.

---

# 7. Просмотр одного сообщения

При выборе строки отображать инспектор.

Показывать:

- topic;
- partition;
- offset;
- timestamp;
- key;
- value;
- headers;
- размер key;
- размер value.

Определять тип содержимого:

- JSON;
- UTF-8 text;
- binary.

Для JSON:

- форматированное представление;
- возможность переключиться на raw.

Для текста:

- raw representation.

Для binary:

- размер;
- ограниченный hex preview;
- экспорт исходных bytes.

Большие payload не должны автоматически форматироваться или рендериться целиком.

Для крупных значений сначала показывать preview и кнопку явной загрузки полного содержимого.

---

# 8. Фильтрация сообщений

Поддержать:

- фильтр по key;
- фильтр по value;
- partition filter.

Поскольку полные сообщения находятся в Rust, фильтрация содержимого должна выполняться преимущественно на backend-стороне.

Frontend отправляет состояние фильтра consumer session.

Rust:

1. применяет фильтр к текущему буферу;
2. возвращает snapshot подходящих строк;
3. применяет фильтр к новым сообщениям.

Смена фильтра не должна перезапускать Kafka consumer.

---

# 9. Producer

Пользователь должен иметь возможность отправить Kafka record.

Поля:

- value;
- optional key;
- optional partition;
- optional headers.

После отправки показать:

- partition;
- offset;
- timestamp.

Использовать долгоживущий `FutureProducer` для активного подключения.

Не создавать новый producer на каждое сообщение.

---

# 10. Consumer Groups

## 10.1. Список

Показывать:

- group ID;
- state;
- member count;
- количество используемых topics.

Поддержать поиск.

## 10.2. Detail

Для группы отображать:

- state;
- members;
- client IDs;
- client hosts;
- assignments;
- committed offsets;
- end offsets;
- lag.

## 10.3. Reset offsets

Поддержать:

- earliest;
- latest;
- timestamp;
- explicit offset.

Перед изменением показать:

- affected group;
- affected topic/partitions;
- старые offsets;
- новые offsets.

Операция должна требовать явного подтверждения.

## 10.4. Delete group

Удалять только группу, которую Kafka позволяет удалить.

Перед удалением требуется подтверждение.

---

# 11. Local Kafka

Сохранить возможность запуска локального Kafka KRaft через:

- Podman;
- Docker.

Логика полностью находится в Rust.

Frontend не получает общую возможность запускать shell-команды.

Backend:

1. определяет доступный runtime;
2. показывает выбранный runtime;
3. запускает контейнер;
4. определяет состояние контейнера;
5. останавливает только контейнер Kafi Kafi.

Команды должны запускаться напрямую с массивом аргументов.

Запрещено формировать shell-команды через конкатенацию строк.

Контейнер должен иметь:

- фиксированное имя;
- label, идентифицирующий Kafi Kafi;
- фиксированную проверенную версию Kafka image.

Приложение не должно удалять или останавливать сторонние контейнеры.

---

# 12. Общая архитектура

Не создавать множество Rust crates без необходимости.

На первом этапе достаточно одного Tauri Rust crate с хорошими внутренними границами.

```text
kafi-kafi/
├── src/
│   ├── app/
│   ├── features/
│   ├── components/
│   ├── ipc/
│   ├── styles/
│   └── main.tsx
│
├── src-tauri/
│   ├── src/
│   │   ├── app/
│   │   ├── domain/
│   │   ├── kafka/
│   │   ├── storage/
│   │   ├── secrets/
│   │   ├── containers/
│   │   ├── ipc/
│   │   ├── error.rs
│   │   ├── lib.rs
│   │   └── main.rs
│   │
│   ├── capabilities/
│   ├── Cargo.toml
│   └── tauri.conf.json
│
├── docs/
├── package.json
├── pnpm-lock.yaml
└── vite.config.ts
```

Выделять отдельные Cargo crates следует только если появится реальная необходимость повторного использования или существенно увеличится кодовая база.

---

# 13. Rust-слои

## 13.1. `domain`

Содержит модели предметной области:

- Cluster;
- Broker;
- Topic;
- Partition;
- KafkaMessage;
- ConsumerGroup;
- ConnectionProfile;
- SecurityConfig.

`domain` ничего не знает о:

- Tauri;
- React;
- IPC;
- конкретных файлах хранения.

## 13.2. `kafka`

Обёртка над `rust-rdkafka`.

Содержит:

```text
admin.rs
connection.rs
consumer.rs
producer.rs
groups.rs
config.rs
```

Прямые обращения к `rust-rdkafka` из Tauri commands запрещены.

## 13.3. `app`

Use-case слой:

```text
connect_cluster
switch_cluster
list_topics
describe_topic
start_consumer
stop_consumer
produce_message
list_groups
reset_offsets
```

Именно этот слой связывает Kafka, storage и IPC.

## 13.4. `ipc`

Единственная граница между frontend и backend.

Содержит:

- Tauri commands;
- channel DTO;
- request DTO;
- response DTO.

DTO не должны совпадать с domain-моделями автоматически.

Это позволяет менять внутренний Rust-код без изменения frontend API.

---

# 14. Состояние backend

Приложение должно иметь центральный `AppState`.

Пример ответственности:

```text
AppState
├── ProfileRepository
├── SecretStore
├── SettingsRepository
├── ConnectionManager
└── ConsumerSessionManager
```

## ConnectionManager

Хранит активное Kafka-подключение.

Активный runtime может содержать:

- AdminClient;
- FutureProducer;
- profile ID;
- generation ID.

При переключении профиля:

1. сначала тестируется новое подключение;
2. только после успешного подключения оно становится активным;
3. старые sessions завершаются;
4. неудачная попытка не должна разрушать рабочее текущее подключение.

---

# 15. ConsumerSessionManager

Каждая открытая вкладка Messages может иметь собственную session.

Session имеет:

```text
sessionId
profileId
topic
consumer
messageBuffer
activeFilter
stats
cancellationToken
channel
```

Все операции выполняются по `sessionId`.

Пример IPC:

```text
start_consumer(request, channel)
pause_consumer(sessionId)
resume_consumer(sessionId)
stop_consumer(sessionId)
set_consumer_filter(sessionId, filter)
get_message_detail(sessionId, messageId)
```

Закрытая session должна полностью удаляться из `AppState`.

Забытых Tokio tasks быть не должно.

---

# 16. Tauri IPC

## Commands

Использовать для запрос → ответ:

```text
get_profiles
save_profile
delete_profile
test_connection
connect
disconnect

get_cluster
get_brokers

list_topics
describe_topic
create_topic
delete_topic
update_topic_config

produce_message

list_consumer_groups
describe_consumer_group
reset_group_offsets
delete_consumer_group

get_settings
save_settings
```

## Channels

Использовать для:

- consumer stream;
- продолжительных операций с progress;
- container logs при необходимости.

Не использовать Tauri global events как основной транспорт Kafka messages.

---

# 17. IPC error model

Все frontend-visible ошибки приводить к единой структуре:

```text
AppError {
    code
    message
    details?
    retryable
}
```

Примеры `code`:

```text
CONNECTION_FAILED
AUTHENTICATION_FAILED
TLS_FAILED
TOPIC_NOT_FOUND
PERMISSION_DENIED
TIMEOUT
INVALID_INPUT
CONSUMER_FAILED
STORAGE_FAILED
CONTAINER_RUNTIME_NOT_FOUND
```

Frontend не должен разбирать текст exception для определения типа ошибки.

Rust stack trace не передаётся пользователю.

---

# 18. Tokio

Tokio используется для:

- Kafka tasks;
- consumer sessions;
- async file operations там, где это оправдано;
- container process handling;
- cancellation;
- batching.

Запрещено:

- блокировать Tauri main thread;
- выполнять долгие synchronous Kafka calls на UI thread;
- удерживать глобальный Mutex во время сетевой операции.

Locks должны использоваться только для коротких операций над состоянием.

---

# 19. Frontend

## 19.1. React

React отвечает только за UI.

Он не должен реализовывать Kafka business logic.

Frontend:

- отображает состояние;
- инициирует команды;
- управляет состоянием вкладок;
- хранит локальное UI state;
- визуализирует данные.

## 19.2. TypeScript

Включить строгий режим:

```text
strict: true
noUncheckedIndexedAccess: true
```

Не использовать `any`, кроме изолированных адаптеров для сторонних библиотек при документированной необходимости.

## 19.3. Global state

Не добавлять Redux или другую глобальную state-библиотеку заранее.

Использовать:

- локальный React state;
- context для небольшого общего UI state;
- специализированный store consumer session только если обычный React state становится узким местом.

Rust остаётся источником истины для Kafka state.

---

# 20. TanStack Table

Использовать для:

- brokers;
- topics;
- partitions;
- consumer groups;
- group offsets.

Функции:

- sorting;
- selection;
- columns;
- resize при необходимости.

Не включать весь возможный API TanStack Table, если он не нужен.

---

# 21. TanStack Virtual

Обязательно использовать для:

- списка Kafka messages;
- очень больших topic/group списков при необходимости.

В DOM должны находиться только видимые и небольшой набор соседних строк.

Нельзя рендерить 10 000–100 000 строк одновременно.

---

# 22. UI

Сохранить текущую концепцию Kafi Kafi:

**Operator Console**.

Принципы:

- плотный технический интерфейс;
- минимум декоративных элементов;
- тёмные графитовые поверхности;
- один основной accent;
- статусные цвета только для статусов;
- таблицы вместо набора карточек;
- панели inspector рядом с данными;
- компактные tabs;
- постоянный статус Kafka connection.

Основная структура:

```text
┌────────┬─────────────────────────────────┐
│        │ Cluster status                  │
│ nav    ├─────────────────────────────────┤
│        │ Tabs                            │
│        ├─────────────────────────────────┤
│        │                                 │
│        │ Workspace                       │
│        │                                 │
└────────┴─────────────────────────────────┘
```

Навигация:

- Connections;
- Cluster;
- Brokers;
- Topics;
- Consumer Groups;
- Settings.

Существующий `DESIGN.md` используется как визуальная база.

Compose-specific значения должны быть преобразованы в CSS variables.

---

# 23. CSS

Не использовать тяжёлый CSS framework только ради базового оформления.

Базовые design tokens:

```css
:root {
    --bg: ...;
    --surface: ...;
    --surface-elevated: ...;
    --border: ...;

    --text-primary: ...;
    --text-secondary: ...;
    --text-muted: ...;

    --accent: ...;

    --success: ...;
    --warning: ...;
    --error: ...;
}
```

Компоненты должны строиться из небольшого набора primitives.

---

# 24. Connection profiles

Модель должна иметь стабильный UUID.

```text
ConnectionProfile
├── id
├── name
├── bootstrapServers[]
├── clientId?
├── securityProtocol
├── sasl?
├── tls?
└── extraProperties
```

Пароли не входят в сериализуемый профиль.

Вместо них профиль содержит ссылки на secrets.

---

# 25. Secrets

Новая версия не должна продолжать хранить основной секретный material тем же способом, что Kotlin-версия.

Использовать системное credential storage:

- Windows Credential Manager;
- macOS Keychain;
- Secret Service / системное хранилище Linux.

В JSON-файлах секреты не хранятся.

Если безопасное системное хранилище недоступно:

- разрешить использовать пароль только для текущей сессии;
- явно сообщить, что пароль не будет сохранён.

Не создавать автоматический plaintext fallback.

Важно: пароль неизбежно кратковременно присутствует в WebView, когда пользователь вводит его. Поэтому:

- не логировать;
- не сохранять в `localStorage`;
- не сохранять в IndexedDB;
- очищать frontend state после Save/Cancel;
- никогда не возвращать сохранённый пароль обратно из Rust.

---

# 26. TLS

Новая архитектура должна поддерживать:

- system CA;
- custom CA;
- PEM certificate;
- PEM private key;
- PKCS#12 keystore.

Проверка hostname включена по умолчанию.

Отключение проверки сертификата не должно быть обычной опцией интерфейса.

Если оно когда-либо добавляется, оно должно быть явно обозначено как небезопасное.

## Legacy JKS

Перед release parity необходимо проверить формат SSL profiles Kotlin-версии.

Если используются JKS:

- определить миграционный путь;
- не пытаться передавать JKS напрямую `librdkafka`;
- обеспечить импорт/конвертацию либо явно запросить у пользователя новый PEM/PKCS#12 файл.

Удалять Kotlin-релиз до решения этого вопроса нельзя.

---

# 27. Local storage

Хранить:

- profiles;
- settings;
- layout preferences;
- version storage schema.

Использовать JSON.

SQLite для этой задачи не нужен.

Файл должен иметь version:

```json
{
  "schemaVersion": 1
}
```

Запись:

1. сериализация;
2. временный файл;
3. flush;
4. атомарная замена основного файла.

Повреждение настроек не должно приводить к потере предыдущей рабочей версии без возможности восстановления.

---

# 28. Миграция данных Kotlin-версии

При первом запуске Rust-версии проверить наличие:

```text
~/.lightkafka/
```

Если найдена старая конфигурация, предложить Import.

Необходимо перенести:

- profiles;
- settings;
- доступные producer settings;
- сохранённые secrets.

Legacy decryptor реализовать отдельным модулем.

Он должен воспроизводить старую схему PBKDF2/AES-GCM только для чтения старых данных.

После успешной миграции:

- secrets сохраняются в новое системное хранилище;
- новые JSON-файлы создаются в новом формате.

Старые файлы не удалять автоматически.

После проверки подключения пользователь может отдельно удалить legacy storage.

---

# 29. Logging

Использовать `tracing`.

Уровни:

- ERROR;
- WARN;
- INFO;
- DEBUG.

Production default:

- INFO.

Логи должны включать:

- lifecycle приложения;
- подключение;
- отключение;
- Kafka operation;
- session start/stop;
- container start/stop;
- продолжительность значимых операций.

Нельзя логировать:

- SASL password;
- private key;
- keystore password;
- bearer token;
- полный Kafka payload;
- чувствительные Kafka config values.

---

# 30. Работа с payload

Полные Kafka values по умолчанию не писать:

- в лог;
- в persistent storage;
- в browser storage.

Message buffer существует только в памяти.

После Stop/закрытия вкладки buffer освобождается.

Kafi Kafi не является локальной базой Kafka messages.

---

# 31. Безопасность Tauri

Использовать минимальный capability set.

Frontend не должен получать общие разрешения:

- filesystem;
- shell;
- process spawning.

Файлы сертификатов выбираются через ограниченный file dialog.

Работа с ними выполняется Rust.

Запретить remote navigation WebView.

Не загружать интерфейс с удалённого URL.

Использовать строгий Content Security Policy.

Открытие внешних ссылок выполняется только через отдельную проверенную команду.

Разрешать только ожидаемые URI schemes, например:

```text
https
```

---

# 32. Производительность

## 32.1. Startup

Цель:

- cold start до интерактивного UI — менее 1,5 секунды на типичном современном desktop CPU.

Измерение проводится отдельно для:

- Windows;
- macOS;
- Linux.

## 32.2. Idle memory

Цель:

- общий RSS приложения вместе с WebView — примерно до 150 МБ;
- 200 МБ считать верхним допустимым ориентиром, требующим анализа.

Это не жёсткий CI threshold, потому что WebView отличается между платформами.

## 32.3. Размер

Целевой размер release package:

- желательно менее 40 МБ;
- более 60 МБ требует объяснения причины.

Не включать в приложение отдельный Chromium.

## 32.4. Message stream

При интенсивном потоке:

- UI остаётся интерактивным;
- память выходит на плато после заполнения bounded buffer;
- скролл не зависит от общего числа просмотренных сообщений;
- Kafka consumer не блокируется рендерингом React.

## 32.5. UI stall

Обычные операции не должны создавать заметных зависаний интерфейса.

JSON formatting, поиск и обработка больших payload должны выполняться лениво либо вне критического render path.

---

# 33. Ограничение больших данных

Не предполагать, что Kafka message маленькое.

Row preview ограничивается по размеру.

Например:

```text
preview ≤ 2 KiB
```

Полный payload загружается по запросу.

Для значений порядка мегабайт:

- не форматировать JSON автоматически;
- показать размер;
- предложить Load full value.

---

# 34. Development toolchain

## Rust

Использовать:

```text
rustfmt
clippy
cargo test
```

CI:

```bash
cargo fmt --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
```

## Frontend

Использовать:

```text
TypeScript
Biome
Vitest
```

Проверки:

```bash
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

Не использовать одновременно ESLint + Prettier + Biome без необходимости.

---

# 35. Version pinning

В репозитории должны быть зафиксированы:

- Rust toolchain;
- pnpm version;
- `Cargo.lock`;
- `pnpm-lock.yaml`.

Не использовать плавающие версии в CI.

Базовая линия на момент начала реализации:

```text
Tauri 2.12.x
rust-rdkafka 0.39.x
React
TypeScript
Vite
Tokio
```

Minor/patch обновления допускаются после CI.

Major upgrades выполняются отдельно.

---

# 36. Сборка librdkafka

Предпочтительна reproducible сборка `librdkafka` вместе с приложением.

Не полагаться на случайную системную версию `librdkafka`.

CI должен собирать необходимый native dependency одинаковым способом для каждого release target.

TLS support должен быть доступен из коробки.

Нельзя требовать от конечного пользователя установки:

- librdkafka;
- OpenSSL development packages;
- Rust;
- Node;
- CMake.

Build dependencies допустимы только на машине сборки.

---

# 37. Тестирование Rust

## Unit

Покрыть:

- validation profiles;
- Kafka config mapping;
- error mapping;
- batching;
- message buffer eviction;
- filtering;
- offset calculations;
- storage migrations;
- secret references.

## Integration

Использовать реальный Kafka container.

Проверить:

- PLAINTEXT connect;
- topic create;
- topic list;
- produce;
- consume earliest/latest;
- explicit offset;
- timestamp;
- consumer group listing;
- lag;
- reset offset;
- delete topic.

Security integration tests добавлять для SSL/SASL отдельно.

---

# 38. Frontend tests

Тестировать пользовательское поведение, а не внутренности React.

Покрыть:

- connection form validation;
- topic filtering;
- session controls;
- message inspector;
- confirmation dialogs;
- error states.

Не создавать большое количество хрупких snapshot tests.

---

# 39. IPC contract tests

Особое внимание уделить соответствию Rust DTO и TypeScript types.

Предпочтительно генерировать frontend-типы из Rust DTO.

Если используется ручное описание типов, CI должен иметь contract tests, обнаруживающие рассинхронизацию.

Типы IPC являются частью архитектурного контракта.

---

# 40. CI

GitHub Actions.

Jobs:

```text
frontend-check
rust-check
rust-test
integration-test
build-windows
build-linux
build-macos-x64
build-macos-arm64
```

Integration tests можно запускать один раз на Linux.

Release builds выполняются на соответствующей ОС.

---

# 41. Release artifacts

## Windows x86_64

- installer;
- portable ZIP.

Portable означает запуск без установки, но пользовательские настройки остаются в обычной системной директории приложения.

## macOS

Отдельные builds:

- Apple Silicon;
- Intel.

Не создавать universal binary, если он заметно увеличивает размер без практической необходимости.

## Linux

Как минимум:

- AppImage x86_64.

Дополнительные пакеты можно добавить позднее.

---

# 42. Release workflow

Release запускается по SemVer tag:

```text
vX.Y.Z
```

CI:

1. выполняет проверки;
2. запускает tests;
3. собирает frontend;
4. собирает Tauri;
5. создаёт platform artifacts;
6. считает SHA-256;
7. публикует GitHub Release.

Нельзя публиковать release, если integration tests не прошли.

---

# 43. Обновления приложения

Автоматический updater не является блокером первого Rust-релиза.

Первоначально достаточно GitHub Releases.

После стабилизации можно добавить подписанный Tauri updater отдельной задачей.

---

# 44. Документация

Обновить:

```text
README.md
PRODUCT.md
DESIGN.md
docs/
AGENTS.md
```

Существующий Toudocu workflow сохранить.

Architecture documentation должна отражать фактический код.

Минимальные документы:

```text
docs/architecture/overview.md
docs/architecture/ipc.md
docs/architecture/message-streaming.md
docs/security/credentials.md
docs/development.md
docs/releasing.md
```

---

# 45. Этапы разработки

## Этап 0 — Baseline и compatibility audit

Задачи:

- зафиксировать текущий функционал Kotlin-версии;
- измерить startup;
- измерить idle memory;
- измерить package size;
- проверить существующие SecurityProtocol/SASL варианты;
- проверить формат SSL/JKS profiles;
- создать migration fixtures;
- зафиксировать пользовательские сценарии для parity.

Результат:

есть конкретный baseline, с которым сравнивается Rust-версия.

---

## Этап 1 — Skeleton

Создать:

- Tauri 2 application;
- React;
- TypeScript;
- Vite;
- pnpm;
- Rust modules;
- basic CI;
- design tokens;
- application shell.

Реализовать:

- navigation rail;
- status bar;
- tabs;
- empty workspace.

На этом этапе Kafka пока не требуется.

---

## Этап 2 — Storage

Реализовать:

- AppSettings;
- ConnectionProfile;
- JSON repository;
- atomic writes;
- system secret store;
- CRUD profiles;
- connection editor UI.

Добавить legacy migration.

---

## Этап 3 — Kafka connection

Реализовать:

- rust-rdkafka configuration;
- test connection;
- ConnectionManager;
- switch connection;
- errors;
- cluster metadata.

Acceptance:

можно создать профиль, подключиться и увидеть brokers.

---

## Этап 4 — Topics

Реализовать:

- topic list;
- search;
- internal filter;
- topic detail;
- partitions;
- config;
- create topic;
- edit config.

Acceptance:

основные административные сценарии текущей версии работают.

---

## Этап 5 — Messages

Реализовать:

- ConsumerSessionManager;
- Tauri Channel;
- bounded Rust buffer;
- batching;
- start positions;
- pause/resume/stop;
- TanStack Virtual;
- row projections;
- lazy detail;
- JSON/text/binary rendering;
- backend filters.

Это наиболее важный performance milestone.

До продолжения проекта провести benchmark.

---

## Этап 6 — Producer

Реализовать:

- producer panel;
- key/value;
- partition;
- headers;
- FutureProducer reuse;
- result metadata.

---

## Этап 7 — Consumer Groups

Реализовать:

- group list;
- detail;
- members;
- offsets;
- lag;
- reset;
- delete.

Все destructive actions требуют confirmation.

---

## Этап 8 — Local Kafka

Перенести:

- Docker detection;
- Podman detection;
- KRaft startup;
- status;
- stop.

---

## Этап 9 — Hardening

Проверить:

- cancellation;
- leaked sessions;
- large payload;
- malformed JSON;
- binary payload;
- reconnect;
- cluster timeout;
- broker unavailable;
- SASL failure;
- TLS failure;
- corrupted storage;
- unavailable credential store.

Провести performance profiling.

---

## Этап 10 — Packaging и migration release

Собрать:

- Windows;
- macOS Intel;
- macOS ARM;
- Linux.

Проверить migration старой версии.

Сравнить с Kotlin baseline.

Только после parity удалить Kotlin implementation из основной ветки.

---

# 46. Критерии parity

Rust-версия считается функционально готовой, если пользователь может без Kotlin-версии:

- импортировать старый профиль;
- создать новый профиль;
- подключиться;
- просмотреть cluster;
- просмотреть brokers;
- просмотреть topics;
- создать topic;
- открыть partitions/config;
- читать messages;
- использовать Earliest/Latest/Offset/Timestamp;
- pause/resume/stop;
- фильтровать messages;
- просматривать JSON/text/binary;
- produce record;
- просматривать consumer groups;
- видеть lag;
- reset offsets;
- удалить допустимую consumer group;
- запускать local Kafka;
- работать на Windows/macOS/Linux.

---

# 47. Definition of Done

Переписывание считается завершённым, когда:

1. Kotlin/JVM больше не нужен для запуска Kafi Kafi.
2. Пользователь не устанавливает дополнительные runtime dependencies.
3. Все основные функции старой версии перенесены.
4. Данные старой версии могут быть мигрированы.
5. Kafka secrets не сохраняются в plaintext.
6. Message stream использует Tauri Channel.
7. Message buffer ограничен.
8. Большие списки виртуализированы.
9. Полные payload не дублируются постоянно в React.
10. Consumer tasks корректно завершаются.
11. Все destructive Kafka actions подтверждаются.
12. CI проходит на всех целевых платформах.
13. Integration tests проходят на реальном Kafka.
14. Release artifacts создаются автоматически.
15. Размер приложения существенно меньше JVM-версии.
16. Startup существенно быстрее JVM-версии.
17. При активном consumer интерфейс остаётся отзывчивым.
18. Нет неограниченного роста RAM.
19. Нет backend-сервера, телеметрии или обязательного облака.
20. README и architecture docs соответствуют новой реализации.

---

# 48. Архитектурные ограничения

Для проекта не следует:

- вводить microservices;
- использовать отдельный локальный HTTP backend;
- поднимать localhost server между React и Rust;
- использовать Electron;
- добавлять SQLite без конкретной необходимости;
- создавать Cargo workspace из большого количества crates заранее;
- отправлять Kafka message по одному Tauri event;
- хранить полный message history во frontend;
- хранить secrets в JSON;
- предоставлять WebView unrestricted shell access;
- смешивать Kafka API непосредственно с React/Tauri command handlers;
- создавать новый producer для каждого send;
- создавать неограниченные очереди сообщений;
- использовать глобальный state manager только потому, что он привычен.

Главный принцип:

> Kafi Kafi должен оставаться небольшим локальным desktop-инструментом: Rust выполняет всю системную и Kafka-логику, Tauri предоставляет минимальную границу IPC, React отвечает за богатый и быстрый интерфейс.