# ZERNEX Files 2.0.0

ZERNEX Files est un gestionnaire Android local, conçu pour naviguer, organiser, inspecter et manipuler les fichiers de l'appareil sans compte, publicité ni envoi automatique de fichiers.

## Identité visuelle 2.0

- Palette stricte : noir, graphite, gris et blanc ; trois accents sémantiques uniquement (rouge, vert, jaune).
- Les icônes de dossiers et de types de fichiers sont prioritaires ; les libellés restent courts.
- Rouge : médias et actions destructives. Vert : documents et états réussis. Jaune : archives, applications et action principale.
- Aucun dégradé multicolore, aucune couleur bleue, violette, cyan ou orange dans l'explorateur.
- Les mêmes règles visuelles s'appliquent au navigateur local et au navigateur SAF.

## Fonctions présentes dans la base 2.0

- Navigation locale et SAF, fil d'Ariane, recherche, filtres par catégorie, tri et vues liste/grille.
- Sélection multiple, copier/couper/coller, renommage groupé, partage, corbeille et restauration.
- Favoris, récents, propriétés, empreinte MD5, taille des dossiers et recherche de doublons.
- Aperçus image, PDF, audio et vidéo ; éditeur texte.
- Archives ZIP, 7Z, TAR, TAR.GZ, TAR.BZ2 et TAR.XZ dans les navigateurs local et SAF ; aperçu, création et extraction ; décompression GZ/BZ2/XZ.
- ZIP AES-256 facultatif ; extraction protégée contre les chemins traversants, liens et collisions.

## Variantes Android

- **Play** : targetSdk 36 ; Android 11+ avec accès étendu demandé explicitement ; SAF pour choisir un dossier.
- **Android 10 Legacy** : targetSdk 29, destinée à la distribution directe ; accès selon les permissions Android 10.
- minSdk 23 ; JDK 17 ; Android SDK API 36.

## Compilation et tests

```bash
./gradlew :app:testPlayDebugUnitTest
./gradlew :app:lintPlayRelease :app:assemblePlayRelease :app:assembleAndroid10LegacyRelease
```

Les APK sont générés dans `app/build/outputs/apk/play/release/` et `app/build/outputs/apk/android10Legacy/release/`.

La signature Release est fournie par les secrets du dépôt. Le certificat doit rester identique entre les mises à jour. Ne jamais commiter le keystore.

## Fonctions non déclarées comme terminées

Les onglets persistants, double panneau, gestionnaire de tâches avec pause/reprise, RAR/RAR5, ISO, SFTP/SMB/WebDAV, Shizuku/root, coffre chiffré, éditeur hexadécimal, gestionnaire d'APK avancé et installation XAPK ne sont pas inclus dans la version 2.0.0 de cette branche. Ils nécessitent des implémentations et tests dédiés.

Validation de la branche V2 : workflow GitHub Actions #93 réussi (13 tests JVM, lint et deux APK Release construits). Attention : aucun keystore de distribution n'est configuré ; les APK de ce workflow sont non signés et ne doivent pas être utilisés comme mise à jour de production.

Voir :
- [Guide utilisateur](docs/GUIDE_UTILISATION_FR_2.0.0.md)
- [Matrice des fonctions et limites](docs/FEATURES_AND_LIMITS_2.0.0.md)
- [Système de design 2.0](docs/V2_DESIGN_SYSTEM.md)
- [Recherche open source et licences](docs/OPEN_SOURCE_RESEARCH_V2.md)
- [Notices des composants tiers](docs/THIRD_PARTY_NOTICES.md)
