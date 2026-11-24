# ZERNEX Files 1.0.4

Gestionnaire de fichiers Android avec accès direct au stockage partagé lorsque Android l'autorise, navigateur SAF en option et interface Material 3 colorée.

## Variantes Android

### `play` — distribution Play Store

- `targetSdk = 36`.
- Android 11 et versions ultérieures : accès direct au stockage partagé après activation de **Gérer tous les fichiers** dans les paramètres Android.
- Android 10 : le système n'offre pas `MANAGE_EXTERNAL_STORAGE` ; le navigateur passe par SAF pour l'accès aux dossiers. Cette limite ne peut pas être supprimée avec une application qui cible une API moderne.
- SAF reste disponible à la demande sur Android 11+ pour ouvrir un emplacement isolé.

Compilation :

```bash
./gradlew :app:assemblePlayRelease
```

### `android10Legacy` — APK de distribution directe pour Android 10

- `targetSdk = 29` et `android:requestLegacyExternalStorage="true"`.
- Demande les permissions Android classiques de lecture/écriture et utilise le volume partagé avec `java.io.File`.
- L'application démarre directement dans le stockage partagé ; elle ne demande pas de sélectionner un dossier SAF.
- Cette variante est destinée aux appareils sous Android 10 (API 29) et à une distribution hors Google Play. Ne pas la publier sur Play Store.

Compilation :

```bash
./gradlew :app:assembleAndroid10LegacyRelease
```

## Génération et vérification

Pour vérifier localement, installer Android SDK (API 36 et Build Tools 36.0.0), JDK 17 et laisser Gradle télécharger les dépendances, puis lancer :

```bash
./gradlew :app:lintPlayRelease :app:lintAndroid10LegacyRelease :app:assemblePlayRelease :app:assembleAndroid10LegacyRelease
```

Les APK sont générés sous :

- `app/build/outputs/apk/play/release/`
- `app/build/outputs/apk/android10Legacy/release/`

La signature de release est facultative en local et peut être fournie par variables d'environnement :
`ZERNEX_KEYSTORE_FILE`, `ZERNEX_KEYSTORE_PASSWORD`, `ZERNEX_KEY_ALIAS`, `ZERNEX_KEY_PASSWORD`.

## Écrans et navigation

Les vues qui portent une tâche complète sont des pages dédiées, avec leur propre barre supérieure et une navigation Retour :

- navigateur principal, recherche et réglages d'affichage ;
- paramètres généraux (sections Affichage, Ouverture, Archives, Actions et Dossiers) ;
- éditeur texte avec état non enregistré, sauvegarde et retour contrôlé ;
- aperçu de fichier et page de propriétés ;
- récents, favoris, corbeille, doublons, stockage et autorisations ;
- en mode SAF : éditeur, aperçu, propriétés, paramètres, favoris et corbeille.

Les fenêtres de dialogue sont réservées aux décisions ponctuelles (création/renommage, confirmation de suppression, conflit de nom ou fermeture avec modifications non enregistrées). Les actions principales ne reposent plus sur un bouton flottant.

## Fonctions conservées

- navigation par chemin et fil d'Ariane, recherche simple et avancée, filtres et sous-dossiers ;
- favoris, récents, dossier de démarrage, affichage liste/grille, fichiers cachés et tri ;
- sélection multiple, sélection totale, renommage individuel et groupé ;
- copier, couper, coller, partage et gestion des collisions ;
- création de dossiers et fichiers texte, éditeur intégré ;
- corbeille, restauration, suppression avec confirmation ;
- création et extraction ZIP, aperçu ZIP ;
- aperçus image, PDF, vidéo et audio en mode local ;
- détails, MD5, taille des dossiers, recherche de doublons et aperçu de certificats/keystores ;
- navigateur SAF avec favoris, recherche, multi-sélection, renommage, transferts, corbeille, propriétés, éditeur texte et ZIP.

Les fournisseurs SAF peuvent limiter certaines actions. Toutes les opérations ne sont donc pas garanties sur chaque fournisseur tiers ou chaque carte SD.

## Licence

Voir `LICENSE` et `docs/THIRD_PARTY_NOTICES.md`.
