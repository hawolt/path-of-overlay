# Code Conventions

## Formatting

Lines exceeding roughly 120 characters are wrapped. Long method calls and constructors are expanded so each argument is on its own indented line with the closing parenthesis on its own line:

```java
someMethod(
        argumentOne,
        argumentTwo,
        argumentThree
        );
```

This applies to constructor and method declarations too - when the signature is long, the opening parenthesis stays on the declaration line, each parameter goes on its own indented line, and the closing parenthesis sits on its own line before the opening brace:

```java
public SettingsOverlay(
        Platform platform,
        Settings settings,
        Runnable onSave,
        Runnable onClose,
        Consumer<Boolean> onSettingsOpenChanged
) {
```

Method chains are broken so each call is on its own line when the full chain would exceed the line limit:

```java
return processBuilder
        .redirectErrorStream(true)
        .start()
        .waitFor() == 0;
```

Arithmetic expressions are only split across lines when the line would otherwise exceed the limit. Short expressions stay on one line regardless of how many operands they have:

```java
// correct - fits on one line
int totalHeight = lines.size() * lineHeight + (lines.size() - 1) * LINE_GAP;

// correct - too long to fit, so split with operator leading the continuation
int x = screenBounds.x + (int) Math.round(
        Config.DEFAULT_X_AT_1440P * (screenBounds.width / (double) Config.REFERENCE_WIDTH)
);
```

## Imports

Always import types explicitly - never use fully qualified names inline in code:

```java
// correct
import java.util.function.Consumer;
...
Consumer<Boolean> onChanged;

// wrong
java.util.function.Consumer<Boolean> onChanged;
```

## Naming

Full descriptive words only - no abbreviations or shorthand:

```java
// correct
int boxHeight;
FontMetrics fontMetrics;
Rectangle gameBounds;

// wrong
int boxH;
FontMetrics fm;
Rectangle gb;
```

## Characters

Do not use the em dash character anywhere - not in strings, log messages, comments, or any other context. Use a regular hyphen instead:

```java
// correct
Logger.info("[Main] No tray - use settings hotkey in-game");

// wrong
Logger.info("[Main] No tray - use settings hotkey in-game");
```

## Comments

No comments anywhere in the code - neither inline nor block nor Javadoc.

## Language

Java 17. `var` is not used - all variable declarations use explicit types.

## Field Ordering

Instance fields at the top of a class are sorted by the length of their declaration, longest first. The length is measured on the type and name only, excluding modifiers (`private`, `final`, etc.):

```java
// correct - longer declarations first
private final Consumer<Boolean> onSettingsOpenChanged;
private final SettingsPanel panel;
private final Platform platform;
private final JDialog dialog;
private final Robot robot;

// wrong - arbitrary or alphabetical order
private final Platform platform;
private final JDialog dialog;
private final SettingsPanel panel;
private final Robot robot;
private final Consumer<Boolean> onSettingsOpenChanged;
```

Static constants (`static final`) follow the same rule within their own group and are kept separate from instance fields.