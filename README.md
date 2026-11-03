# Contador Truco · versión ligera

App de tanteador de truco argentino para Android, nativa en Java y Canvas, sin bibliotecas de terceros ni permisos de red.

## Funciones
- Dos equipos con nombres editables.
- Partidas a 15 o 30 puntos, malas y buenas, palitos exactos de cinco.
- Botones +1, +2, +3, +4, −1; deshacer, reinicio confirmado, historial desplegable.
- Guardado automático local con SharedPreferences.

## Compilar desde GitHub Actions
1. Entrá en **Actions** > **Compilar APK Android**.
2. Abrí la ejecución disparada por el último commit o pulsá **Run workflow**.
3. Descargá el artefacto **ContadorTruco-debug-apk**, que contiene `app-debug.apk`.

## Compilar localmente
Abrí el repositorio con Android Studio, instalá JDK 17 y Android SDK 35, sincronizá Gradle y elegí **Build > Build APK(s)**. El workflow utiliza Gradle 8.9.

La interfaz es dibujada con primitivas Canvas y evita imágenes pesadas. El tamaño de APK y el consumo real deben medirse tras compilarlo.
