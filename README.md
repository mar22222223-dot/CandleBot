# CandleBot - Bot de trading Android

## Description
Application Android qui utilise la caméra pour détecter les bougies rouges sur un graphique
de trading et clique automatiquement sur le bouton "Buy" via l'API AccessibilityService.

## Comment compiler (Android Studio - gratuit)

### Étape 1 — Installer Android Studio
Télécharge sur : https://developer.android.com/studio
(Windows, Mac ou Linux)

### Étape 2 — Ouvrir le projet
1. Lance Android Studio
2. "Open" → sélectionne le dossier `CandleBot`
3. Attends la synchronisation Gradle (2-3 minutes)

### Étape 3 — Générer l'APK
1. Menu : **Build → Build Bundle(s) / APK(s) → Build APK(s)**
2. Attends la compilation
3. Clique sur "locate" dans la notification pour trouver le fichier `.apk`

### Étape 4 — Installer sur ton téléphone
1. Active **"Sources inconnues"** sur ton Android :
   Paramètres → Sécurité → Installer des apps inconnues
2. Transfère le fichier `.apk` sur ton téléphone (USB, email, etc.)
3. Ouvre le fichier pour installer

## Comment utiliser l'app

### Première utilisation
1. Lance **CandleBot**
2. Autorise l'accès à la **caméra**
3. Appuie sur **"Activer le service de clic automatique"**
4. Dans les Paramètres d'accessibilité → trouve **"CandleBot"** → Active-le
5. Reviens dans l'app

### Utilisation
1. Ouvre ton app de trading (Binance, MetaTrader, etc.)
2. Lance CandleBot **par-dessus** (avec la fenêtre flottante, ou reviens à CandleBot)
3. Pointe la caméra vers ton graphique
4. Ajuste le **seuil** (défaut: 10 bougies rouges avant de Buy)
5. Ajuste la **sensibilité** (1=strict, 5=très sensible)
6. Appuie sur **"Démarrer"**

### Comment fonctionne le clic automatique
- **Priorité 1** : cherche un bouton avec le texte "Buy", "Acheter", "Long", etc.
- **Priorité 2** : cherche dans les descriptions d'accessibilité
- **Priorité 3** : clic gestuel en bas-droite de l'écran (position par défaut du bouton Buy)

### Ajuster la position du clic de secours
Si le bouton Buy est ailleurs, modifie dans `CandleBotAccessibilityService.java` :
```java
float x = metrics.widthPixels * 0.75f;  // 75% de la largeur
float y = metrics.heightPixels * 0.85f; // 85% de la hauteur
```

## Structure du projet
```
CandleBot/
├── app/src/main/
│   ├── AndroidManifest.xml         ← Permissions
│   ├── java/com/candlebot/
│   │   ├── MainActivity.java        ← UI + détection couleur
│   │   └── CandleBotAccessibilityService.java  ← Vrais clics système
│   └── res/
│       ├── layout/activity_main.xml ← Interface utilisateur
│       ├── xml/accessibility_service_config.xml
│       └── values/                  ← Strings, styles
├── app/build.gradle                 ← Dépendances
└── README.md
```

## Permissions utilisées
- `CAMERA` — pour voir le graphique
- `BIND_ACCESSIBILITY_SERVICE` — pour effectuer les vrais clics
- `VIBRATE` — vibration quand BUY est déclenché

