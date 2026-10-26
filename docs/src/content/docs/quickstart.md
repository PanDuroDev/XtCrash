---
title: Quick Start
description: Requirements, source-based installation, minimal setup, and a deliberate test crash.
---

## Requirements

- Android API 23 or newer.
- To build XtCrash from source: JDK 21, Android SDK with compile SDK 37, Gradle wrapper 9.4.1, and Android Gradle Plugin 9.2.1.

## Installation status

The intended JitPack coordinates use version `v1.0.0`, but the tag/artifact must be published and verified before these dependencies can be relied on. Until then, treat the coordinates as pending:

```kotlin
dependencies {
    implementation("com.github.SoufianoDev.XtCrash:xtcrash:v1.0.0")
    // Optional Compose integration:
    implementation("com.github.SoufianoDev.XtCrash:xtcrash-compose:v1.0.0")
}
```

Add JitPack to dependency resolution when using a published artifact. Check the [JitPack project page](https://jitpack.io/#SoufianoDev/XtCrash) for current publication status.

### Build the core library locally

```bash
git clone https://github.com/SoufianoDev/XtCrash.git
cd XtCrash
./gradlew :library:assembleRelease
```

The AAR is created under `library/build/outputs/aar/`. The optional Compose module can be built with `./gradlew :xtcrash-compose:assembleRelease`.

## Configure once

The core manifest declares `CrashInitProvider`. No manual installation call or manifest entry is required for the default error screen.

```kotlin
import android.app.Application
import io.github.soufianodev.xtcrash.config.CrashConfig

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashConfig.Builder.create()
            .enabled(true)
            .showErrorDetails(true)
            .trackActivities(true)
            .apply()
    }
}
```

The public builder is also callable from Java:

```java
import android.app.Application;
import io.github.soufianodev.xtcrash.config.CrashConfig;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashConfig.Builder.create()
                .enabled(true)
                .showErrorDetails(true)
                .trackActivities(true)
                .apply();
    }
}
```

## Verify the integration

Trigger a deliberate crash from a temporary test action:

```kotlin
throw RuntimeException("XtCrash test crash")
```

The configured error screen should open in a separate process. Remove the deliberate crash after confirming the behavior.

## Next steps

- [Configuration](../configuration/) for defaults, labels, colors, and behavior.
- [Custom Error Screen](../custom-error-screen/) for a custom activity and manifest setup.
- [Crash Reports](../crash-reports/) for destination routing and privacy behavior.
- [Jetpack Compose](../compose/) for Compose-specific integration paths.
