#!/bin/sh
exec gradle "$@"
```

**4.** Commit changes

Ensuite on va aussi modifier le `build.yml` pour utiliser Gradle directement sans `gradlew`. C'est plus simple.

Retourne dans `.github/workflows/build.yml` → crayon ✏️ → remplace la ligne :
```
chmod +x gradlew
./gradlew assembleDebug
```
Par :
```
gradle assembleDebug
