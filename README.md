# NRV Game Agent

Экспериментальный Android-first AI game agent.

Цель проекта: исследовать, может ли агент научиться понимать игровую сцену, принимать решения и со временем превосходить человеческий baseline в контролируемой тренировочной среде.

## Первый этап

- Android-приложение запускает разрешённый пользователем захват экрана через MediaProjection.
- CaptureService получает кадры и формирует телеметрию.
- GameState описывает наблюдаемое состояние.
- RuleAgent принимает базовое решение без платного API.
- Simulator позволяет проверять логику агента без реальной онлайн-игры.
- Следующий этап: CV-модель (ONNX/TFLite), датасет и панель Rustam vs AI.

## Архитектура

```text
Android screen -> CaptureService -> Vision -> GameState -> Agent -> Decision
                                             |
                                             +-> telemetry/training data
```

Проект сознательно не содержит обходов античита, скрытия автоматизации или механизмов уклонения от обнаружения.

## Сборка

Требования:

- JDK 17
- Android SDK
- Gradle 9.6+

```bash
gradle :app:assembleDebug
```

CI собирает debug APK на каждый push/PR.
